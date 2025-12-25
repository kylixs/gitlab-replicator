package com.gitlab.mirror.common.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login Request
 * <p>
 * Request model for login authentication
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * Username
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * Challenge code
     */
    @NotBlank(message = "挑战码不能为空")
    private String challenge;

    /**
     * Client proof (64-character hex string)
     */
    @NotBlank(message = "客户端证明不能为空")
    @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "客户端证明必须是64位十六进制字符串")
    private String clientProof;
}
