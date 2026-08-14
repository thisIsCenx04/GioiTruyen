package com.storyplatform.auth.application.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    // Length alone lets "password" through, so the pattern asks for one capital
    // and one digit as well. Deliberately no punctuation requirement: it pushes
    // people towards "Password1!" rather than towards a longer passphrase.
    @NotBlank(message = "Chưa nhập mật khẩu.")
    @Size(min = 8, max = 128, message = "Mật khẩu phải từ 8 đến 128 ký tự.")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
            message = "Mật khẩu cần ít nhất một chữ in hoa và một chữ số."
    )
    String password,

    // The sign-up form has always asked for this; until now the API had nowhere
    // to put it, and sending it failed the whole request as an unknown property.
    //
    // @NotNull is not redundant next to @AssertTrue: bean validation treats a
    // null as valid for @AssertTrue, so without it a caller could skip the
    // field entirely and create an account having agreed to nothing.
    @NotNull(message = "Bạn cần đồng ý với điều khoản sử dụng để tạo tài khoản.")
    @AssertTrue(message = "Bạn cần đồng ý với điều khoản sử dụng để tạo tài khoản.")
    Boolean acceptedTerms
) {}
