package com.yumu.noveltranslator.controller.web;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.service.UserService;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Web 术语库管理接口
 * 路径前缀: /user/glossaries
 */
@RestController
@RequestMapping("/user/glossaries")
@RequiredArgsConstructor
public class WebGlossaryController {

    private final UserService userService;

    /**
     * 获取术语库列表
     * GET /user/glossaries
     */
    @GetMapping
    public Result<List<GlossaryResponse>> getGlossaryList() {
        Long userId = SecurityUtil.getRequiredUserId();
        List<GlossaryResponse> glossaries = userService.getGlossaryList(userId);
        return Result.ok(glossaries);
    }

    /**
     * 获取术语库详情
     * GET /user/glossaries/{id}
     */
    @GetMapping("/{id}")
    public Result<GlossaryResponse> getGlossaryDetail(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        GlossaryResponse glossary = userService.getGlossaryDetail(userId, id);
        if (glossary == null) {
            return Result.error("术语库不存在");
        }
        return Result.ok(glossary);
    }

    /**
     * 创建术语项
     * POST /user/glossaries
     */
    @PostMapping
    public Result<GlossaryResponse> createGlossaryItem(@RequestBody @Valid GlossaryItemRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        GlossaryResponse glossary = userService.createGlossaryItem(userId, request);
        return Result.ok(glossary);
    }

    /**
     * 更新术语项
     * PUT /user/glossaries/{id}
     */
    @PutMapping("/{id}")
    public Result<GlossaryResponse> updateGlossaryItem(@PathVariable Long id, @RequestBody @Valid GlossaryItemRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        GlossaryResponse glossary = userService.updateGlossaryItem(userId, id, request);
        if (glossary == null) {
            return Result.error("术语项不存在");
        }
        return Result.ok(glossary);
    }

    /**
     * 删除术语项
     * DELETE /user/glossaries/{id}
     */
    @DeleteMapping("/{id}")
    public Result deleteGlossaryItem(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        boolean success = userService.deleteGlossaryItem(userId, id);
        if (!success) {
            return Result.error("术语项不存在");
        }
        return Result.ok(null);
    }

    /**
     * 获取术语列表
     * GET /user/glossaries/{id}/terms
     */
    @GetMapping("/{id}/terms")
    public Result<List<GlossaryTermResponse>> getGlossaryTerms(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        GlossaryResponse glossary = userService.getGlossaryDetail(userId, id);
        if (glossary == null) {
            return Result.error("术语库不存在");
        }
        List<GlossaryTermResponse> terms = userService.getGlossaryTerms(userId);
        return Result.ok(terms);
    }
}
