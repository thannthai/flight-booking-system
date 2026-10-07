mà
### TECH STACK
- Java 21, Spring Boot 3.5.x
- jjwt 0.12.x (Không dùng io.jsonwebtoken bản cũ)
- Spring Data JPA + Hibernate (Không dùng EntityManager trực tiếp)
- Jakarta Validation (Không dùng javax)
- Lombok
- PostgreSQL + Flyway
- Redis (Spring Data Redis)

### QUY TẮC CODE 
1. Không @Autowired field injection — dùng @RequiredArgsConstructor + final field để thực hiện Constructor Injection.
2. Không dùng @Data trên JPA Entity — dùng riêng lẻ @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder để tránh lỗi StackOverflowError do vòng lặp bidirectional.
3. Mọi response trả về cho Client bắt buộc phải bọc qua ApiResponse<T>.
4. Không throw exception trực tiếp trong lớp Controller — chỉ throw AppException(ErrorCode.xxx) trong Service layer để GlobalExceptionHandler tự động bắt và format.
5. Validation dữ liệu đầu vào sử dụng các annotations của Jakarta Validation.
6. Không tự ý sử dụng Reflection (Class.forName, Method.invoke, Field.set, Constructor.newInstance...) hoặc các kỹ thuật dynamic code phức tạp trong business logic. Hãy ưu tiên các cơ chế chuẩn của Spring Boot như Dependency Injection, Interface và Polymorphism.
7. Ưu tiên tính dễ đọc (readability) hơn là viết code mẹo phức tạp (clever code). Giữ cấu trúc tường minh, dễ debug.
8. Nếu có nhiều cách implement, hãy ưu tiên giải pháp phù hợp với kiến trúc đã thiết kế, phản ánh cách làm phổ biến trong các hệ thống production thực tế, đủ cho phạm vi MVP và tránh over-engineering. Không được đơn giản hóa bằng cách bỏ qua các yêu cầu quan trọng về: Security, Concurrency, Transaction, Data consistency và Business rules cốt lõi.
9. Khi viết code, nếu có các business rule hoặc kỹ thuật quan trọng (ví dụ: tránh double booking bằng atomic update, idempotency check khi nhận webhook), hãy ghi chú comment ngắn gọn để giải thích lý do.
10. Không tự ý thêm package, class, design pattern hoặc API ngoài tài liệu nếu tôi chưa yêu cầu. Phát hiện điểm bất hợp lý -> KHÔNG tự sửa, hãy nêu lý do và hỏi lại tôi trước.

### QUY TRÌNH & WORKFLOW PHẠM VI
1. Chỉ code đúng module và phân đoạn được yêu cầu. Không viết trước các module khác.
2. Quy trình viết code sẽ chia theo từng bước lớn (Phase):
   - Phase 1: Entity + Repository -> DỪNG và chờ tôi xác nhận.
   - Phase 2: DTO + Mapper -> DỪNG và chờ tôi xác nhận.
   - Phase 3: Service Layer (Xử lý logic nghiệp vụ) -> DỪNG và chờ tôi xác nhận.
   - Phase 4: Controller Layer + Validation + Swagger -> DỪNG và chờ tôi xác nhận.
3. Trước khi sinh code cho mỗi Phase, hãy giải thích ngắn gọn bằng 2-3 dòng tại sao lại thiết kế cấu trúc lớp như vậy để tôi học tập.
