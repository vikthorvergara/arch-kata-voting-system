from datetime import datetime
from airflow.decorators import dag, task
from airflow.datasets import Dataset

raw_sales = Dataset("db://sales/raw_sales")
clean_sales = Dataset("db://sales/clean_sales")
top_sales_by_city = Dataset("db://sales/top_sales_by_city")
top_salesman_country = Dataset("db://sales/top_salesman_country")


@dag(schedule="@daily", start_date=datetime(2024, 1, 1), catchup=False)
def sales_pipeline():

    @task(outlets=[raw_sales])
    def ingest_raw_sales():
        print("Ingesting raw sales from CSV source...")
        return {"rows_ingested": 1000}

    @task(inlets=[raw_sales], outlets=[clean_sales])
    def clean_sales_data():
        print("Cleaning and validating sales records...")
        return {"rows_clean": 980, "rows_dropped": 20}

    @task(inlets=[clean_sales], outlets=[top_sales_by_city])
    def aggregate_top_sales_by_city():
        results = [
            {"city": "Sao Paulo", "total_sales": 52000.0, "rank": 1},
            {"city": "Rio de Janeiro", "total_sales": 43000.0, "rank": 2},
            {"city": "Belo Horizonte", "total_sales": 31000.0, "rank": 3},
        ]
        print(f"Top cities computed: {results}")
        return results

    @task(inlets=[clean_sales], outlets=[top_salesman_country])
    def aggregate_top_salesman():
        results = [
            {"salesman_id": "S001", "name": "Ana Lima", "total_sales": 28000.0, "rank": 1},
            {"salesman_id": "S042", "name": "Carlos Melo", "total_sales": 24500.0, "rank": 2},
        ]
        print(f"Top salesmen computed: {results}")
        return results

    ingested = ingest_raw_sales()
    cleaned = clean_sales_data()
    ingested >> cleaned
    cleaned >> [aggregate_top_sales_by_city(), aggregate_top_salesman()]


sales_pipeline()
