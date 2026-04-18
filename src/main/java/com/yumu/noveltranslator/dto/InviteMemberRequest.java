package com.yumu.noveltranslator.dto;

import com.yumu.noveltranslator.enums.ProjectMemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteMemberRequest {

    @Email(message = "邮箱格式不正确")
    private String email;

    @NotNull(message = "角色不能为空")
    private ProjectMemberRole role;
}
