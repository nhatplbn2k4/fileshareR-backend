package com.example.fileshareR.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // System errors
    INTERNAL_SERVER_ERROR(9999, "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR(1003, "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    BAD_REQUEST(1004, "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED(1005, "Phương thức HTTP không được hỗ trợ", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_FOUND(1006, "Không tìm thấy dữ liệu yêu cầu", HttpStatus.NOT_FOUND),

    // User authentication errors
    USER_NOT_FOUND(4001, "Người dùng không tồn tại", HttpStatus.NOT_FOUND),
    USER_NOT_ACTIVE(4002, "Tài khoản người dùng không hoạt động", HttpStatus.BAD_REQUEST),
    INVALID_REFRESH_TOKEN(4003, "Refresh token không hợp lệ", HttpStatus.UNAUTHORIZED),
    INVALID_ACCESS_TOKEN(4004, "Access token không hợp lệ", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(4005, "Bạn không có quyền truy cập tài nguyên này", HttpStatus.FORBIDDEN),
    INVALID_CURRENT_PASSWORD(4006, "Mật khẩu hiện tại không đúng", HttpStatus.BAD_REQUEST),
    PASSWORD_MISMATCH(4007, "Mật khẩu mới và xác nhận mật khẩu không khớp", HttpStatus.BAD_REQUEST),
    SAME_PASSWORD(4008, "Mật khẩu mới không được trùng với mật khẩu hiện tại", HttpStatus.BAD_REQUEST),
    EMAIL_EXISTED(4009, "Email đã tồn tại", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(4010, "Email hoặc mật khẩu không đúng", HttpStatus.UNAUTHORIZED),

    // Document errors
    DOCUMENT_NOT_FOUND(5001, "Không tìm thấy tài liệu", HttpStatus.NOT_FOUND),
    DOCUMENT_ACCESS_DENIED(5002, "Bạn không có quyền truy cập tài liệu này", HttpStatus.FORBIDDEN),
    UNSUPPORTED_FILE_TYPE(5003, "Loại file không được hỗ trợ. Chỉ chấp nhận PDF, DOCX, DOC, TXT",
            HttpStatus.BAD_REQUEST),

    // Folder errors
    FOLDER_NOT_FOUND(6001, "Không tìm thấy thư mục", HttpStatus.NOT_FOUND),
    FOLDER_ACCESS_DENIED(6002, "Bạn không có quyền truy cập thư mục này", HttpStatus.FORBIDDEN),
    PARENT_FOLDER_NOT_FOUND(6003, "Không tìm thấy thư mục cha", HttpStatus.NOT_FOUND),
    SHARE_LOGIN_REQUIRED(6004, "Vui lòng đăng nhập để xem thư mục được chia sẻ qua link", HttpStatus.UNAUTHORIZED),

    // OTP and Email errors
    OTP_NOT_FOUND(7001, "Mã OTP không hợp lệ", HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(7002, "Mã OTP đã hết hạn", HttpStatus.BAD_REQUEST),
    OTP_NOT_VERIFIED(7003, "Vui lòng xác thực OTP trước khi thực hiện thao tác", HttpStatus.BAD_REQUEST),
    EMAIL_SEND_FAILED(7004, "Gửi email thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
    EMAIL_ALREADY_VERIFIED(7005, "Email đã được xác thực", HttpStatus.BAD_REQUEST),
    EMAIL_NOT_VERIFIED(7006, "Email chưa được xác thực", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL(7007, "Email không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),

    // Group errors
    GROUP_NOT_FOUND(8001, "Không tìm thấy nhóm", HttpStatus.NOT_FOUND),
    GROUP_MEMBER_NOT_FOUND(8002, "Không tìm thấy thành viên trong nhóm", HttpStatus.NOT_FOUND),
    GROUP_ACCESS_DENIED(8003, "Bạn không có quyền truy cập nhóm này", HttpStatus.FORBIDDEN),
    GROUP_ADMIN_REQUIRED(8004, "Chỉ quản trị viên nhóm mới được thực hiện thao tác này", HttpStatus.FORBIDDEN),
    GROUP_OWNER_REQUIRED(8005, "Chỉ chủ nhóm mới được thực hiện thao tác này", HttpStatus.FORBIDDEN),
    GROUP_PRIVATE_ACCESS_DENIED(8006, "Nhóm riêng tư không cho phép người ngoài truy cập", HttpStatus.FORBIDDEN),
    GROUP_UPLOAD_BANNED(8007, "Bạn đang bị cấm upload trong nhóm", HttpStatus.FORBIDDEN),
    GROUP_DOCUMENT_NOT_FOUND(8008, "Không tìm thấy tài liệu nhóm", HttpStatus.NOT_FOUND),
    GROUP_FOLDER_NOT_FOUND(8009, "Không tìm thấy thư mục nhóm", HttpStatus.NOT_FOUND),
    GROUP_ALREADY_MEMBER(8010, "Bạn đã là thành viên của nhóm", HttpStatus.BAD_REQUEST),
    GROUP_JOIN_NOT_ALLOWED(8011, "Không thể tham gia nhóm riêng tư", HttpStatus.FORBIDDEN),
    GROUP_JOIN_REQUEST_PENDING(8012, "Bạn đã gửi yêu cầu tham gia, vui lòng chờ duyệt", HttpStatus.BAD_REQUEST),
    GROUP_JOIN_REQUEST_NOT_FOUND(8013, "Không tìm thấy yêu cầu tham gia", HttpStatus.NOT_FOUND),

    // Storage quota errors
    USER_STORAGE_QUOTA_EXCEEDED(9001, "Vượt quá dung lượng lưu trữ của bạn. Vui lòng nâng cấp gói hoặc mua thêm bộ nhớ.", HttpStatus.BAD_REQUEST),
    GROUP_STORAGE_QUOTA_EXCEEDED(9002, "Vượt quá dung lượng lưu trữ của nhóm. Chủ nhóm cần nâng cấp gói hoặc mua thêm bộ nhớ.", HttpStatus.BAD_REQUEST),
    PLAN_NOT_FOUND(9003, "Không tìm thấy gói lưu trữ", HttpStatus.NOT_FOUND),
    ADDON_NOT_FOUND(9004, "Không tìm thấy gói mua thêm", HttpStatus.NOT_FOUND),

    // Convert errors
    CONVERT_PDF_ONLY(9101, "Chỉ hỗ trợ chuyển đổi từ file PDF", HttpStatus.BAD_REQUEST),
    CONVERT_FAILED(9102, "Chuyển đổi tài liệu thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
    CONVERT_CREDITS_EXCEEDED(9103, "Dịch vụ CloudConvert đã hết hạn mức tháng này. Hệ thống sẽ tạm dùng engine cơ bản, vui lòng thử lại.", HttpStatus.SERVICE_UNAVAILABLE),
    CONVERT_AUTH_FAILED(9104, "Xác thực với dịch vụ CloudConvert thất bại", HttpStatus.SERVICE_UNAVAILABLE),

    // Moderation errors
    MODERATION_NOT_PENDING(9201, "Tài liệu này không ở trạng thái chờ duyệt", HttpStatus.BAD_REQUEST),
    MODERATION_PERMISSION_DENIED(9202, "Chỉ admin hoặc chủ nhóm mới được duyệt tài liệu", HttpStatus.FORBIDDEN),
    MODERATION_NOT_GROUP_DOCUMENT(9203, "Chỉ tài liệu trong nhóm mới có trạng thái kiểm duyệt", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
