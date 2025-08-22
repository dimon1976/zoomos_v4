package com.java.controller;

import com.java.constants.UrlConstants;
import com.java.model.FileOperation;
import com.java.repository.FileOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер для обеспечения обратной совместимости со старыми URL
 * Обрабатывает старые пути и перенаправляет на новые унифицированные URL
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class LegacyRedirectController {

    private final FileOperationRepository fileOperationRepository;

    /**
     * Перенаправление со старого URL статуса операции на новый
     * /import/status/{operationId} -> /clients/{clientId}/operations/{operationId}
     */
    @GetMapping("/import/status/{operationId}")
    public String redirectImportStatus(@PathVariable Long operationId,
                                     RedirectAttributes redirectAttributes) {
        log.debug("Legacy redirect запрос: /import/status/{}", operationId);

        try {
            FileOperation operation = fileOperationRepository.findById(operationId)
                    .orElseThrow(() -> new IllegalArgumentException("Операция не найдена"));

            // Перенаправляем на новый унифицированный URL
            String newUrl = UrlConstants.CLIENT_OPERATION_DETAIL
                    .replace("{clientId}", operation.getClient().getId().toString())
                    .replace("{operationId}", operationId.toString());

            log.debug("Перенаправление на новый URL: {}", newUrl);
            
            redirectAttributes.addFlashAttribute("infoMessage", 
                "Вы были перенаправлены на новую страницу статуса операций");
            
            return "redirect:" + newUrl;

        } catch (Exception e) {
            log.error("Ошибка при перенаправлении legacy URL /import/status/{}", operationId, e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Операция не найдена или устарела");
            return "redirect:" + UrlConstants.CLIENTS;
        }
    }

    /**
     * Перенаправление других legacy URL импорта
     */
    @GetMapping("/import/{clientId}/status/{operationId}")
    public String redirectOldImportStatus(@PathVariable Long clientId, 
                                        @PathVariable Long operationId) {
        log.debug("Legacy redirect запрос: /import/{}/status/{}", clientId, operationId);
        
        String newUrl = UrlConstants.CLIENT_OPERATION_DETAIL
                .replace("{clientId}", clientId.toString())
                .replace("{operationId}", operationId.toString());
        
        return "redirect:" + newUrl;
    }

    /**
     * Перенаправление со старых URL операций на новые
     */
    @GetMapping("/operations/{operationId}")
    public String redirectOperationStatus(@PathVariable Long operationId,
                                        RedirectAttributes redirectAttributes) {
        log.debug("Legacy redirect запрос: /operations/{}", operationId);

        try {
            FileOperation operation = fileOperationRepository.findById(operationId)
                    .orElseThrow(() -> new IllegalArgumentException("Операция не найдена"));

            String newUrl = UrlConstants.CLIENT_OPERATION_DETAIL
                    .replace("{clientId}", operation.getClient().getId().toString())
                    .replace("{operationId}", operationId.toString());

            return "redirect:" + newUrl;

        } catch (Exception e) {
            log.error("Ошибка при перенаправлении legacy URL /operations/{}", operationId, e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Операция не найдена");
            return "redirect:" + UrlConstants.CLIENTS;
        }
    }

    /**
     * Перенаправление на главную страницу клиента
     */
    @GetMapping("/client/{clientId}")
    public String redirectToClient(@PathVariable Long clientId) {
        log.debug("Legacy redirect запрос: /client/{}", clientId);
        
        return "redirect:" + UrlConstants.CLIENT_DETAIL
                .replace("{clientId}", clientId.toString());
    }

    /**
     * Обработка других возможных legacy URL
     */
    @GetMapping("/status/{operationId}")
    public String redirectGeneralStatus(@PathVariable Long operationId,
                                      RedirectAttributes redirectAttributes) {
        log.debug("Generic legacy redirect запрос: /status/{}", operationId);
        
        // Перенаправляем через основной метод
        return redirectOperationStatus(operationId, redirectAttributes);
    }
}