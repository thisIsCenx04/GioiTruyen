package com.storyplatform.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Chưa nhập email.")
    @Email(message = "Email không hợp lệ.")
    String email,

    // Optional: the sign-up form asks only for an email, so a username is
    // derived from it when the caller does not supply one.
    @Size(min = 3, max = 100, message = "Tên đăng nhập phải từ 3 đến 100 ký tự.")
    String username,

    // The upper bound matches the form's own maxLength; capping at 50 rejected
    // the long passphrase the hint text actively encourages.
    @NotBlank(message = "Chưa nhập mật khẩu.")
    @Size(min = 8, max = 128, message = "Mật khẩu phải từ 8 đến 128 ký tự.")
    String password
) {}
