package com.yumu.noveltranslator.controller.web;

import com.yumu.noveltranslator.dto.*;
import com.yumu.noveltranslator.entity.Glossary;
import com.yumu.noveltranslator.service.UserService;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
    private final com.yumu.noveltranslator.mapper.GlossaryMapper glossaryMapper;

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
    public Result<List<GlossaryResponse>> getGlossaryTerms(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        GlossaryResponse glossary = userService.getGlossaryDetail(userId, id);
        if (glossary == null) {
            return Result.error("术语库不存在");
        }
        List<GlossaryResponse> terms = userService.getGlossaryTerms(userId);
        return Result.ok(terms);
    }

    /**
     * 导出术语表为 CSV
     * GET /user/glossaries/export
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportGlossary() {
        Long userId = SecurityUtil.getRequiredUserId();
        List<GlossaryResponse> glossaries = userService.getGlossaryList(userId);

        StringBuilder csv = new StringBuilder();
        csv.append("﻿"); // BOM for Excel UTF-8
        csv.append("source_word,target_word,remark\n");
        for (GlossaryResponse g : glossaries) {
            csv.append(csvEscape(g.getSourceWord())).append(',');
            csv.append(csvEscape(g.getTargetWord())).append(',');
            csv.append(csvEscape(g.getRemark() != null ? g.getRemark() : "")).append('\n');
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"glossary.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从 CSV 文件导入术语
     * POST /user/glossaries/import
     */
    @PostMapping("/import")
    public Result<Integer> importGlossary(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtil.getRequiredUserId();
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return Result.error("仅支持 CSV 文件");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return Result.error("文件格式错误");
            }

            int imported = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = parseCsvLine(line);
                if (parts.length < 2) continue;

                String sourceWord = parts[0].trim();
                String targetWord = parts[1].trim();
                String remark = parts.length > 2 ? parts[2].trim() : "";

                if (sourceWord.isEmpty() || targetWord.isEmpty()) continue;

                Glossary glossary = new Glossary();
                glossary.setUserId(userId);
                glossary.setSourceWord(sourceWord);
                glossary.setTargetWord(targetWord);
                glossary.setRemark(remark.isEmpty() ? null : remark);
                glossaryMapper.insert(glossary);
                imported++;
            }

            return Result.ok(imported);
        } catch (IOException e) {
            return Result.error("文件解析失败: " + e.getMessage());
        }
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String[] parseCsvLine(String line) {
        List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    result.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }
}
