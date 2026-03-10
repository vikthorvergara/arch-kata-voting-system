import uuid
from datetime import datetime
from openlineage.client.client import OpenLineageClient
from openlineage.client.event_v2 import RunEvent, RunState, Run, Job, InputDataset, OutputDataset
from openlineage.client.facet_v2 import SchemaDatasetFacet, SchemaField

MARQUEZ_URL = "http://localhost:5000"
NAMESPACE = "sales-pipeline"

client = OpenLineageClient(
    transport={"type": "http", "url": MARQUEZ_URL, "endpoint": "api/v1/lineage"}
)


def run_job(job_name, inputs, outputs):
    run_id = str(uuid.uuid4())
    now = datetime.now().isoformat()

    client.emit(RunEvent(
        eventType=RunState.START,
        eventTime=now,
        run=Run(runId=run_id),
        job=Job(namespace=NAMESPACE, name=job_name),
        producer="poc-1-manual",
        inputs=inputs,
        outputs=outputs,
    ))

    client.emit(RunEvent(
        eventType=RunState.COMPLETE,
        eventTime=datetime.now().isoformat(),
        run=Run(runId=run_id),
        job=Job(namespace=NAMESPACE, name=job_name),
        producer="poc-1-manual",
        inputs=inputs,
        outputs=outputs,
    ))

    print(f"[{job_name}] emitted run_id={run_id}")


raw_schema = SchemaDatasetFacet(fields=[
    SchemaField("sale_id", "STRING"),
    SchemaField("salesman_id", "STRING"),
    SchemaField("city", "STRING"),
    SchemaField("amount", "DOUBLE"),
    SchemaField("sale_date", "DATE"),
    SchemaField("raw_payload", "STRING"),
])

clean_schema = SchemaDatasetFacet(fields=[
    SchemaField("sale_id", "STRING"),
    SchemaField("salesman_id", "STRING"),
    SchemaField("city", "STRING"),
    SchemaField("amount", "DOUBLE"),
    SchemaField("sale_date", "DATE"),
])

top_city_schema = SchemaDatasetFacet(fields=[
    SchemaField("city", "STRING"),
    SchemaField("total_sales", "DOUBLE"),
    SchemaField("rank", "INT"),
])

top_salesman_schema = SchemaDatasetFacet(fields=[
    SchemaField("salesman_id", "STRING"),
    SchemaField("name", "STRING"),
    SchemaField("total_sales", "DOUBLE"),
    SchemaField("rank", "INT"),
])

run_job(
    "ingest_raw_sales",
    inputs=[InputDataset(NAMESPACE, "file://data/raw_sales.csv")],
    outputs=[OutputDataset(NAMESPACE, "raw_sales", facets={"schema": raw_schema})],
)

run_job(
    "clean_sales",
    inputs=[InputDataset(NAMESPACE, "raw_sales")],
    outputs=[OutputDataset(NAMESPACE, "clean_sales", facets={"schema": clean_schema})],
)

run_job(
    "aggregate_top_sales_by_city",
    inputs=[InputDataset(NAMESPACE, "clean_sales")],
    outputs=[OutputDataset(NAMESPACE, "top_sales_by_city", facets={"schema": top_city_schema})],
)

run_job(
    "aggregate_top_salesman",
    inputs=[InputDataset(NAMESPACE, "clean_sales")],
    outputs=[OutputDataset(NAMESPACE, "top_salesman_country", facets={"schema": top_salesman_schema})],
)

print(f"\nLineage graph: http://localhost:3000/lineage/dataset/{NAMESPACE}/clean_sales")
