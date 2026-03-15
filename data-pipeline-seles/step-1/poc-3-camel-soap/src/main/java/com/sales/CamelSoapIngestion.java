package com.sales;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
        cxfEndpoint.setAddress("http://0.0.0.0:8080/shipping");
        cxfEndpoint.setWsdlURL("classpath:wsdl/SalesService.wsdl");
        cxfEndpoint.setServiceName("{http://sales.com/wsdl}SalesService");
        cxfEndpoint.setPortName("{http://sales.com/wsdl}SalesPort");
        cxfEndpoint.setDataFormat(org.apache.camel.component.cxf.common.DataFormat.PAYLOAD);
        cxfEndpoint.setCamelContext(context);

        context.getRegistry().bind("shippingCxf", cxfEndpoint);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("cxf:bean:shippingCxf")
                        .process(exchange -> {
                            @SuppressWarnings("unchecked")
                            CxfPayload<?> payload = (CxfPayload<?>) exchange.getIn().getBody();
                            Element root = (Element) payload.getBody().get(0);

                            String saleId = getChildText(root, "saleId");
                            String shippingMethod = getChildText(root, "shippingMethod");
                            String trackingNumber = getChildText(root, "trackingNumber");
                            String deliveryAddress = getChildText(root, "deliveryAddress");
                            String estimatedDelivery = getChildText(root, "estimatedDelivery");

                            String json = String.format(
                                    "{\"sale_id\":\"%s\",\"shipping_method\":\"%s\",\"tracking_number\":\"%s\",\"delivery_address\":\"%s\",\"estimated_delivery\":\"%s\"}",
                                    saleId, shippingMethod, trackingNumber,
                                    deliveryAddress.replace("\"", "\\\""), estimatedDelivery);

                            System.out.println("SOAP shipping received: " + json);
                            producer.send(new ProducerRecord<>("sales.shipping", saleId, json));
                            producer.flush();

                            String response = "<ns:SubmitShippingResponse xmlns:ns=\"http://sales.com/wsdl\">"
                                    + "<ns:status>OK</ns:status>"
                                    + "<ns:message>Shipping submitted to Kafka</ns:message>"
                                    + "</ns:SubmitShippingResponse>";
                            exchange.getMessage().setBody(response);
                        });
            }
        });

        context.start();
        System.out.println("SOAP shipping endpoint started at http://localhost:8080/shipping");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                producer.close();
                context.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        Thread.sleep(60000);
        producer.close();
        context.stop();
    }

    private static String getChildText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        nodes = parent.getElementsByTagName(localName);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        return "";
    }
}
