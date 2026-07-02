package com.java.controller.utils;

import com.java.dto.reportfetcher.ReportConfigDto;
import com.java.dto.reportfetcher.ReportRunStatusDto;
import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportRunStatus;
import com.java.repository.ClientRepository;
import com.java.repository.ReportRunRepository;
import com.java.service.reportfetcher.ReportConfigService;
import com.java.service.reportfetcher.ReportRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Controller
@RequestMapping("/utils/report-fetcher")
@RequiredArgsConstructor
@Slf4j
public class ReportFetcherController {

    private final ReportConfigService reportConfigService;
    private final ReportRunService reportRunService;
    private final ReportRunRepository reportRunRepository;
    private final ClientRepository clientRepository;

    @GetMapping
    public String list(@RequestParam(required = false) Long clientId, Model model) {
        model.addAttribute("pageTitle", "Report Fetcher");
        model.addAttribute("configs", reportConfigService.findAll(clientId));
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("selectedClientId", clientId);
        return "utils/report-fetcher-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("pageTitle", "Новый конфиг отчёта");
        model.addAttribute("config", new ReportConfigDto());
        model.addAttribute("clients", clientRepository.findAll());
        return "utils/report-fetcher-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", "Редактирование конфига отчёта");
        model.addAttribute("config", reportConfigService.toDto(id));
        model.addAttribute("clients", clientRepository.findAll());
        return "utils/report-fetcher-form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute ReportConfigDto dto, RedirectAttributes redirectAttributes) {
        try {
            reportConfigService.save(dto);
            redirectAttributes.addFlashAttribute("success", "Конфиг сохранён");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/utils/report-fetcher";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reportConfigService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Конфиг удалён");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/utils/report-fetcher";
    }

    @PostMapping("/{id}/lookup-file")
    public String uploadLookupFile(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                    RedirectAttributes redirectAttributes) {
        try {
            reportConfigService.attachLookupFile(id, file);
            redirectAttributes.addFlashAttribute("success", "Справочник загружен: " + file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Ошибка загрузки справочника для конфига {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Ошибка загрузки: " + e.getMessage());
        }
        return "redirect:/utils/report-fetcher/" + id + "/edit";
    }

    @GetMapping("/{id}/lookup-file")
    public ResponseEntity<Resource> downloadLookupFile(@PathVariable Long id) throws IOException {
        ReportConfig config = reportConfigService.getEntity(id);
        if (config.getLookupFileStoredPath() == null) {
            throw new IllegalStateException("Файл-справочник не загружен");
        }
        Path path = Path.of(config.getLookupFileStoredPath());
        if (!Files.exists(path)) {
            throw new IllegalStateException("Файл-справочник указан в конфиге, но отсутствует на диске: " + path);
        }
        byte[] data = Files.readAllBytes(path);
        String filename = config.getLookupFileOriginalName() != null
                ? config.getLookupFileOriginalName() : path.getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(data));
    }

    @PostMapping("/{id}/run")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> run(@PathVariable Long id) {
        try {
            ReportRun run = reportRunService.startRun(id);
            return ResponseEntity.ok(Map.of("runId", run.getId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{configId}/history")
    public String history(@PathVariable Long configId, Model model) {
        ReportConfig config = reportConfigService.getEntity(configId);
        model.addAttribute("pageTitle", "История запусков — " + config.getName());
        model.addAttribute("config", config);
        model.addAttribute("runs", reportRunRepository.findAllByConfigIdOrderByCreatedAtDesc(configId));
        return "utils/report-fetcher-history";
    }

    @GetMapping("/runs/{runId}")
    @ResponseBody
    public ResponseEntity<ReportRunStatusDto> runStatus(@PathVariable Long runId) {
        ReportRun run = reportRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run не найден: " + runId));
        return ResponseEntity.ok(new ReportRunStatusDto(run.getId(), run.getStatus().name(), run.getErrorMessage()));
    }

    @GetMapping("/runs/{runId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long runId) throws IOException {
        ReportRun run = reportRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run не найден: " + runId));
        if (run.getStatus() != ReportRunStatus.DONE || run.getResultFilePath() == null) {
            throw new IllegalStateException("Результат ещё не готов");
        }
        Path path = Path.of(run.getResultFilePath());
        byte[] data = Files.readAllBytes(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(data));
    }
}
