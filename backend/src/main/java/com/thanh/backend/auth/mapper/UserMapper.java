package com.thanh.backend.auth.mapper;

import com.thanh.backend.auth.dto.request.RegisterRequest;
import com.thanh.backend.auth.dto.response.UserResponse;
import com.thanh.backend.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper cho User entity.
 *
 * componentModel = "spring": MapStruct generate UserMapperImpl với @Component
 * → Spring tự quản lý như một Bean, inject bình thường qua @RequiredArgsConstructor.
 *
 * MapStruct tự generate UserMapperImpl vào target/generated-sources khi compile
 * — KHÔNG tạo thủ công file đó.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    /**
     * Map RegisterRequest → User entity.
     * passwordHash và role bị bỏ qua — AuthService tự set sau khi BCrypt encode.
     */
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "role", ignore = true)
    User toEntity(RegisterRequest request);

    /**
     * Map User entity → UserResponse DTO trả về client.
     */
    UserResponse toResponse(User user);
}
