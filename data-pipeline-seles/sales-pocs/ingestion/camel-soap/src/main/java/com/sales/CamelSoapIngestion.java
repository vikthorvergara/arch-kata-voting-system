package com.sales;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.Properties;

public class CamelSoapIngestion {

    public static void main(String[] args) throws Exception {
        Properties kafkaProps = new Properties();
        kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps);

        CamelContext context = new DefaultCamelContext();

        CxfEndpoint cxfEndpoint = new CxfEndpoint();
        cxfEndpoint.setAddress("http://0.0.0.0:8080/sales");
        cxfEndpoint.setWsdlURL("classpath:wsdl/SalesService.wsdl");
        cxfEndpoint.setServiceName("{http://sales.com/wsdl}SalesService");
        cxfEndpoint.setPortName("{http://sales.com/wsdl}SalesPort");
        cxfEndpoint.setDataFormat(org.apache.camel.component.cxf.common.DataFormat.PAYLOAD);
        cxfEndpoint.setCamelContext(context);

        context.addComponent("salesEndpoint", cxfEndpoint.getComponent());
        context.getRegistry().bind("salesCxf", cxfEndpoint);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("cxf:bean:salesCxf")
                        .process(exchange -> {
                            String xmlBody = extractBody(exchange);
                            String json = xmlToJson(xmlBody);
                            System.out.println("SOAP request received, sending to Kafka: " + json);
                            producer.send(new ProducerRecord<>("sales.soap", json));
                            producer.flush();

                            String response = "<ns:SubmitSaleResponse xmlns:ns=\"http://sales.com/wsdl\">"
                                    + "<ns:status>OK</ns:status>"
                                    + "<ns:message>Sale submitted to Kafka</ns:message>"
                                    + "</ns:SubmitSaleResponse>";
                            exchange.getMessage().setBody(response);
                        });
            }
        });

        context.start();
        System.out.println("SOAP endpoint started at http://localhost:8080/sales");

        Thread.sleep(30000);
        System.out.println("Shutting down.");
        producer.close();
        context.stop();
    }

    private static String extractBody(Exchange exchange) {
        try {
            Object body = exchange.getIn().getBody();
            if (body instanceof org.apache.camel.component.cxf.common.message.CxfPayload) {
                @SuppressWarnings("unchecked")
                org.apache.camel.component.cxf.common.message.CxfPayload<org.apache.cxf.binding.soap.SoapHeader> payload =
                        (org.apache.camel.component.cxf.common.message.CxfPayload<org.apache.cxf.binding.soap.SoapHeader>) body;
                if (!payload.getBody().isEmpty()) {
                    org.w3c.dom.Element element = (org.w3c.dom.Element) payload.getBody().get(0);
                    StringWriter writer = new StringWriter();
                    TransformerFactory.newInstance().newTransformer()
                            .transform(new DOMSource(element), new StreamResult(writer));
                    return writer.toString();
                }
            }
            return exchange.getIn().getBody(String.class);
        } catch (Exception e) {
            return exchange.getIn().getBody(String.class);
        }
    }

    private static String xmlToJson(String xml) {
        try {
            javax.xml.parsers.DocumentBuilder builder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
            doc.getDocumentElement().normalize();

            String salesmanName = getElementText(doc, "salesmanName");
            String city = getElementText(doc, "city");
            String country = getElementText(doc, "country");
            String amount = getElementText(doc, "amount");

            return String.format("{\"salesmanName\":\"%s\",\"city\":\"%s\",\"country\":\"%s\",\"amount\":%s}",
                    salesmanName, city, country, amount);
        } catch (Exception e) {
            return "{\"raw\":\"" + xml.replace("\"", "\\\"") + "\"}";
        }
    }

    private static String getElementText(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        nodes = doc.getElementsByTagNameNS("*", tagName);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        return "";
    }
}
