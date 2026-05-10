package com.yumu.noveltranslator.adapter.in.rest.web;

import com.yumu.noveltranslator.port.dto.common.Result;
import com.yumu.noveltranslator.port.dto.common.PageResponse;
import com.yumu.noveltranslator.port.dto.translation.GlossaryResponse;
import com.yumu.noveltranslator.port.dto.translation.GlossaryItemRequest;
import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import com.yumu.noveltranslator.port.in.GlossaryPort;
import com.yumu.noveltranslator.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Web 术语库管理接口
 * 路径前缀: /user/glossaries
 */
@RestController
@RequestMapping("/user/glossaries")
@RequiredArgsConstructor
@Slf4j
public class WebGlossaryController {

    private final GlossaryPort glossaryPort;

    /**
     * 获取术语库列表
     * GET /user/glossaries
     */
    @GetMapping
    public Result<PageResponse<GlossaryResponse>> getGlossaryList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String search) {
        Long userId = SecurityUtil.getRequiredUserId();
        PageResponse<GlossaryResponse> result = glossaryPort.listGlossaries(userId, page, pageSize, search);
        return Result.ok(result);
    }

    /**
     * 获取术语库详情
     * GET /user/glossaries/{id}
     */
    @GetMapping("/{id}")
    public Result<GlossaryResponse> getGlossaryDetail(@PathVariable Long id) {
        Long userId = SecurityUtil.getRequiredUserId();
        GlossaryResponse glossary = glossaryPort.getGlossaryDetail(userId, id);
        if (glossary == null) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "术语库不存在");
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
        GlossaryResponse glossary = glossaryPort.createGlossaryItem(userId, request);
        return Result.ok(glossary);
    }

    /**
     * 更新术语项
     * PUT /user/glossaries/{id}
     */
    @PutMapping("/{id}")
    public Result<GlossaryResponse> updateGlossaryItem(@PathVariable Long id, @RequestBody @Valid GlossaryItemRequest request) {
        Long userId = SecurityUtil.getRequiredUserId();
        GlossaryResponse glossary = glossaryPort.updateGlossaryItem(userId, id, request);
        if (glossary == null) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "术语项不存在");
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
        boolean success = glossaryPort.deleteGlossaryItem(userId, id);
        if (!success) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "术语项不存在");
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
        GlossaryResponse glossary = glossaryPort.getGlossaryDetail(userId, id);
        if (glossary == null) {
            return Result.error(ErrorCodeEnum.NOT_FOUND, "术语库不存在");
        }
        List<GlossaryResponse> terms = glossaryPort.getAllGlossaryTerms(userId);
        return Result.ok(terms);
    }

    /**
     * 导出术语表为 CSV
     * GET /user/glossaries/export
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportGlossary() {
        Long userId = SecurityUtil.getRequiredUserId();
        List<GlossaryResponse> glossaries = glossaryPort.getAllGlossaryTerms(userId);

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
            return Result.error(ErrorCodeEnum.PARAMETER_ERROR, "文件不能为空");
        }

        // 文件大小限制：最大 5MB
        final long MAX_FILE_SIZE = 5 * 1024 * 1024;
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error(ErrorCodeEnum.PAYLOAD_TOO_LARGE, "文件大小超过限制（最大 5MB）");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return Result.error(ErrorCodeEnum.PARAMETER_ERROR, "仅支持 CSV 文件");
        }

        // MIME 类型校验
        String contentType = file.getContentType();
        if (contentType != null && !contentType.contains("csv") && !contentType.contains("text") && !contentType.contains("plain")) {
            return Result.error(ErrorCodeEnum.PARAMETER_ERROR, "不支持的文件类型");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return Result.error(ErrorCodeEnum.PARAMETER_ERROR, "文件格式错误");
            }

            List<GlossaryItemRequest> items = new ArrayList<>();
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

                GlossaryItemRequest item = new GlossaryItemRequest();
                item.setSourceWord(sourceWord);
                item.setTargetWord(targetWord);
                item.setRemark(remark.isEmpty() ? null : remark);
                items.add(item);
            }

            if (items.isEmpty()) {
                return Result.error(ErrorCodeEnum.PARAMETER_ERROR, "CSV 文件中没有有效数据");
            }

            log.info("开始导入术语表: userId={}, fileName={}, lines={}", userId, filename, items.size());
            int imported = glossaryPort.importGlossaryCsv(userId, items);
            log.info("术语表导入完成: imported={}", imported);
            return Result.ok(imported > 0 ? imported : 0);
        } catch (IOException e) {
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR, "文件解析失败: " + e.getMessage());
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
        List<String> result = new ArrayList<>();
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
