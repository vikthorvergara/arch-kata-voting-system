CREATE TABLE payments (
    sale_id UUID PRIMARY KEY,
    salesman_name VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    country VARCHAR(100) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_status VARCHAR(50) NOT NULL,
    payment_date TIMESTAMP DEFAULT NOW()
);

ALTER TABLE payments REPLICA IDENTITY FULL;
