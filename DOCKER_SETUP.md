# Hướng dẫn Setup Database với Docker

## Yêu cầu
- Docker và Docker Compose đã được cài đặt
- Port 5432 chưa được sử dụng (hoặc thay đổi port trong docker-compose.yml)

## Bước 1: Khởi động PostgreSQL Database

```bash
# Khởi động database
docker-compose up -d

# Kiểm tra status
docker-compose ps

# Xem logs
docker-compose logs -f postgres
```

## Bước 2: Cấu hình Database

Database sẽ được tự động tạo với thông tin sau (từ file .env):
- **Database name**: document_management
- **Username**: admin
- **Password**: admin123
- **Port**: 5432
- **Host**: localhost

## Bước 3: Chạy ứng dụng Spring Boot

```bash
# Sử dụng Maven Wrapper
./mvnw spring-boot:run

# Hoặc build và chạy
./mvnw clean package
java -jar target/fileshareR-0.0.1-SNAPSHOT.jar
```

## Các lệnh Docker hữu ích

```bash
# Dừng database
docker-compose down

# Dừng và xóa volumes (XÓA TOÀN BỘ DATA!)
docker-compose down -v

# Restart database
docker-compose restart

# Truy cập PostgreSQL CLI
docker exec -it filesharer-postgres psql -U admin -d document_management

# Backup database
docker exec filesharer-postgres pg_dump -U admin document_management > backup.sql

# Restore database
docker exec -i filesharer-postgres psql -U admin document_management < backup.sql
```

## Kết nối database từ các công cụ khác

### DBeaver / pgAdmin
- Host: localhost
- Port: 5432
- Database: document_management
- Username: admin
- Password: admin123

### Connection URL
```
jdbc:postgresql://localhost:5432/document_management
```

## Thay đổi cấu hình

Chỉnh sửa file `.env` để thay đổi:
- Tên database
- Username/Password
- Hoặc các thông số khác

Sau khi thay đổi, restart container:
```bash
docker-compose down
docker-compose up -d
```

## Troubleshooting

### Port 5432 đã được sử dụng
Thay đổi port mapping trong `docker-compose.yml`:
```yaml
ports:
  - "5433:5432"  # Sử dụng port 5433 thay vì 5432
```

Và cập nhật connection URL trong `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/filesharer_db
```

### Container không start được
```bash
# Xem logs chi tiết
docker-compose logs postgres

# Xóa và tạo lại
docker-compose down -v
docker-compose up -d
```

### Không kết nối được từ ứng dụng
1. Kiểm tra container đang chạy: `docker-compose ps`
2. Kiểm tra logs: `docker-compose logs postgres`
3. Test connection: `docker exec filesharer-postgres pg_isready`
4. Kiểm tra firewall/antivirus có block port 5432 không
