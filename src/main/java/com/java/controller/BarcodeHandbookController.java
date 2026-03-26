package com.java.controller;

import com.java.dto.FileAnalysisResultDto;
import com.java.dto.handbook.BhSearchConfigDto;
import com.java.dto.handbook.DomainRenameResult;
import com.java.mapper.FileMetadataMapper;
import com.java.model.entity.BhBrand;
import com.java.model.entity.BhBrandSynonym;
import com.java.model.entity.BhDomain;
import com.java.model.entity.FileMetadata;
import com.java.service.handbook.BhBrandService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import com.java.service.file.FileAnalyzerService;
import com.java.service.handbook.BarcodeHandbookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Контроллер справочника штрихкодов.
 * Маршруты: /handbook/*
 */
@Controller
@RequestMapping("/handbook")
@RequiredArgsConstructor
@Slf4j
public class BarcodeHandbookController {

    private final BarcodeHandbookService handbookService;
    private final FileAnalyzerService fileAnalyzerService;
    private final BhBrandService bhBrandService;

    // =========================================================================
    // Главная страница справочника + AJAX поиск
    // =========================================================================

    @GetMapping({"", "/"})
    public String index(Model model) {
        model.addAttribute("pageTitle", "Справочник штрихкодов");
        model.addAttribute("stats", handbookService.getStats());
        return "handbook/index";
    }

    /** AJAX: поиск по штрихкоду / части наименования / части URL */
    @GetMapping("/lookup")
    @ResponseBody
    public List<Map<String, Object>> lookup(@RequestParam(defaultValue = "") String q) {
        return handbookService.searchForUi(q);
    }

    // =========================================================================
    // Импорт (инструкции — фактический импорт идёт через /import системы)
    // =========================================================================

    @GetMapping("/import")
    public String importInstructions(Model model) {
        model.addAttribute("pageTitle", "Импорт в справочник");
        return "handbook/import";
    }

    // =========================================================================
    // Поиск по справочнику: upload → configure → process
    // =========================================================================

    @GetMapping("/search")
    public String searchUpload(Model model) {
        model.addAttribute("pageTitle", "Поиск по справочнику");
        return "handbook/search";
    }

    @PostMapping("/search/upload")
    public String searchUploadFile(@RequestParam("file") MultipartFile file,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Выберите файл для загрузки");
            return "redirect:/handbook/search";
        }
        try {
            FileMetadata metadata = fileAnalyzerService.analyzeFile(file, "handbook-search");
            session.setAttribute("bhSearchFile", metadata);
            return "redirect:/handbook/search/configure";
        } catch (Exception e) {
            log.error("Ошибка анализа файла для поиска в справочнике: {}", file.getOriginalFilename(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка анализа файла: " + e.getMessage());
            return "redirect:/handbook/search";
        }
    }

    @GetMapping("/search/configure")
    public String searchConfigure(Model model, HttpSession session) {
        FileMetadata metadata = (FileMetadata) session.getAttribute("bhSearchFile");
        if (metadata == null) {
            return "redirect:/handbook/search";
        }
        FileAnalysisResultDto analysisResult = FileMetadataMapper.toAnalysisDto(metadata);
        model.addAttribute("pageTitle", "Настройка поиска");
        model.addAttribute("fileMetadata", analysisResult);
        model.addAttribute("searchConfig", new BhSearchConfigDto());
        model.addAttribute("allDomains", handbookService.getAllDomains());
        return "handbook/search-configure";
    }

    @PostMapping("/search/process")
    public ResponseEntity<Resource> searchProcess(@ModelAttribute BhSearchConfigDto config,
                                                   HttpSession session) {
        FileMetadata metadata = (FileMetadata) session.getAttribute("bhSearchFile");
        if (metadata == null) {
            throw new IllegalArgumentException("Файл не найден в сессии. Загрузите файл заново.");
        }
        try {
            byte[] result = handbookService.searchAndExport(metadata, config);
            session.removeAttribute("bhSearchFile");

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String ext = "CSV".equalsIgnoreCase(config.getOutputFormat()) ? "csv" : "xlsx";
            String fileName = "handbook-search-" + timestamp + "." + ext;

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new ByteArrayResource(result));
        } catch (Exception e) {
            log.error("Ошибка поиска по справочнику", e);
            throw new RuntimeException("Ошибка поиска: " + e.getMessage(), e);
        }
    }

    @PostMapping("/search/cancel")
    public String searchCancel(HttpSession session) {
        session.removeAttribute("bhSearchFile");
        return "redirect:/handbook/search";
    }

    // =========================================================================
    // Управление доменами
    // =========================================================================

    @GetMapping("/domains")
    public String domains(Model model) {
        model.addAttribute("pageTitle", "Домены справочника");
        model.addAttribute("domains", handbookService.getAllDomains());
        return "handbook/domains";
    }

    /** AJAX: поиск доменов (возвращает JSON-список) */
    @GetMapping("/domains/search")
    @ResponseBody
    public List<Map<String, Object>> domainsSearch(@RequestParam(defaultValue = "") String q) {
        return handbookService.searchDomains(q).stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "domain", d.getDomain(),
                        "urlCount", d.getUrlCount(),
                        "isActive", d.getIsActive() != null && d.getIsActive()
                ))
                .toList();
    }

    /** Переключить активность домена */
    @PostMapping("/domains/{id}/toggle")
    public String domainToggle(@PathVariable Long id, RedirectAttributes ra) {
        try {
            BhDomain d = handbookService.toggleDomain(id);
            ra.addFlashAttribute("success",
                    "Домен " + d.getDomain() + " " + (Boolean.TRUE.equals(d.getIsActive()) ? "включён" : "отключён"));
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Ошибка: " + e.getMessage());
        }
        return "redirect:/handbook/domains";
    }

    /** AJAX: предварительный просмотр переименования домена */
    @GetMapping("/domains/{id}/rename/preview")
    @ResponseBody
    public Map<String, Object> domainRenamePreview(@PathVariable Long id,
                                                    @RequestParam String newDomain) {
        try {
            DomainRenameResult result = handbookService.previewRenameDomain(id, newDomain);
            return Map.of(
                    "updatedUrls", result.updatedUrls(),
                    "deletedDuplicates", result.deletedDuplicates(),
                    "merged", result.merged(),
                    "targetDomain", result.targetDomain()
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /** Пересчитать url_count для всех доменов из bh_urls */
    @PostMapping("/domains/recalculate-counts")
    public String recalculateDomainCounts(RedirectAttributes ra) {
        try {
            int updated = handbookService.recalculateDomainCounts();
            ra.addFlashAttribute("success", "Счётчики пересчитаны: обновлено " + updated + " доменов");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Ошибка пересчёта: " + e.getMessage());
        }
        return "redirect:/handbook/domains";
    }

    /** Выполнить переименование/слияние домена */
    @PostMapping("/domains/{id}/rename")
    public String domainRename(@PathVariable Long id,
                               @RequestParam String newDomain,
                               RedirectAttributes ra) {
        try {
            DomainRenameResult result = handbookService.renameDomain(id, newDomain);
            String msg = result.merged()
                    ? String.format("Домен объединён с '%s': обновлено %d URL, удалено %d дублей",
                        result.targetDomain(), result.updatedUrls(), result.deletedDuplicates())
                    : String.format("Домен переименован в '%s': обновлено %d URL, удалено %d дублей",
                        result.targetDomain(), result.updatedUrls(), result.deletedDuplicates());
            ra.addFlashAttribute("success", msg);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Ошибка переименования: " + e.getMessage());
        }
        return "redirect:/handbook/domains";
    }

    // =========================================================================
    // Управление брендами
    // =========================================================================

    @GetMapping("/brands")
    public String brands(Model model) {
        model.addAttribute("pageTitle", "Управление брендами");
        model.addAttribute("totalBrands", bhBrandService.countBrands());
        return "handbook/brands";
    }

    /** AJAX: пагинированный список брендов с синонимами */
    @GetMapping("/brands/list")
    @ResponseBody
    public Map<String, Object> brandsList(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<BhBrand> brandsPage = bhBrandService.getBrandsPage(q, page, size);
        List<Map<String, Object>> list = brandsPage.getContent().stream().map(b -> {
            List<Map<String, Object>> synonyms = b.getSynonyms() == null ? List.of() :
                b.getSynonyms().stream()
                    .map(s -> Map.<String, Object>of("id", s.getId(), "synonym", s.getSynonym()))
                    .toList();
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("name", b.getName());
            m.put("synonyms", synonyms);
            return m;
        }).toList();
        return Map.of(
            "brands", list,
            "totalElements", brandsPage.getTotalElements(),
            "totalPages", brandsPage.getTotalPages(),
            "currentPage", brandsPage.getNumber(),
            "hasNext", brandsPage.hasNext(),
            "hasPrev", brandsPage.hasPrevious()
        );
    }

    /** AJAX: создать бренд */
    @PostMapping("/brands/create")
    @ResponseBody
    public ResponseEntity<?> createBrandAjax(@RequestParam String name) {
        try {
            BhBrand brand = bhBrandService.createBrand(name);
            return ResponseEntity.ok(Map.of("id", brand.getId(), "name", brand.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** AJAX: удалить бренд */
    @PostMapping("/brands/{id}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteBrandAjax(@PathVariable Long id) {
        try {
            bhBrandService.deleteBrand(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** AJAX: добавить синоним */
    @PostMapping("/brands/{id}/synonyms")
    @ResponseBody
    public ResponseEntity<?> addSynonymAjax(@PathVariable Long id, @RequestParam String synonym) {
        try {
            BhBrandSynonym s = bhBrandService.addSynonym(id, synonym);
            return ResponseEntity.ok(Map.of("id", s.getId(), "synonym", s.getSynonym()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** AJAX: удалить синоним */
    @PostMapping("/brands/{id}/synonyms/{sid}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteSynonymAjax(@PathVariable Long id, @PathVariable Long sid) {
        try {
            bhBrandService.deleteSynonym(sid);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/brands/import-from-products")
    public String importBrandsFromProducts(RedirectAttributes ra) {
        try {
            Map<String, Integer> result = bhBrandService.importFromProducts();
            ra.addFlashAttribute("success",
                    String.format("Загружено брендов из справочника: создано %d, пропущено %d",
                            result.get("created"), result.get("skipped")));
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Ошибка: " + e.getMessage());
        }
        return "redirect:/handbook/brands";
    }
}
