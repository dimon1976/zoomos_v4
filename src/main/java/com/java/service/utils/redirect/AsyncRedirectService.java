package com.java.service.utils.redirect;

import com.java.dto.NotificationDto;
import com.java.model.utils.RedirectProcessingRequest;
import com.java.model.utils.RedirectResult;
import com.java.service.notification.NotificationService;
import com.java.service.utils.RedirectFinderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncRedirectService {
    
    private final RedirectFinderService redirectFinderService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    
    @Async("redirectTaskExecutor")
    public CompletableFuture<String> processRedirectsAsync(RedirectProcessingRequest request) {
        String operationId = UUID.randomUUID().toString();
        
        try {
            log.info("Начинаем асинхронную обработку редиректов. Operation ID: {}", operationId);
            
            // Уведомляем о начале обработки
            sendProgressUpdate(operationId, "Начинаем обработку редиректов...", 0, request.getUrls().size());
            
            List<RedirectResult> results = redirectFinderService.processRedirects(
                request.getUrls(), 
                request.getMaxRedirects(), 
                request.getTimeoutMs(),
                request.getDelayMs(),
                (processed, total) -> {
                    // Прогресс колбэк
                    int percentage = (int) ((processed * 100.0) / total);
                    sendProgressUpdate(operationId, 
                        String.format("Обработано %d из %d URLs", processed, total), 
                        percentage, total);
                }
            );
            
            // Генерируем файл
            sendProgressUpdate(operationId, "Генерируем файл результатов...", 95, request.getUrls().size());
            
            String fileName = "redirect-finder-result_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".csv";
            
            byte[] fileData = redirectFinderService.generateResultFile(results, request, fileName);
            
            // Уведомляем о завершении
            sendCompletionNotification(operationId, fileName, results.size());
            
            log.info("Асинхронная обработка редиректов завершена. Operation ID: {}", operationId);
            
            return CompletableFuture.completedFuture(fileName);
            
        } catch (Exception e) {
            log.error("Ошибка асинхронной обработки редиректов. Operation ID: {}", operationId, e);
            
            sendErrorNotification(operationId, "Ошибка обработки: " + e.getMessage());
            
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private void sendProgressUpdate(String operationId, String message, int percentage, int total) {
        try {
            RedirectProgressDto progress = new RedirectProgressDto();
            progress.setOperationId(operationId);
            progress.setMessage(message);
            progress.setPercentage(percentage);
            progress.setProcessed(percentage * total / 100);
            progress.setTotal(total);
            progress.setStatus("IN_PROGRESS");
            progress.setTimestamp(LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/redirect-progress/" + operationId, progress);
            
        } catch (Exception e) {
            log.error("Ошибка отправки прогресса для операции {}", operationId, e);
        }
    }
    
    private void sendCompletionNotification(String operationId, String fileName, int processedCount) {
        try {
            RedirectProgressDto progress = new RedirectProgressDto();
            progress.setOperationId(operationId);
            progress.setMessage("Обработка завершена успешно");
            progress.setPercentage(100);
            progress.setProcessed(processedCount);
            progress.setTotal(processedCount);
            progress.setStatus("COMPLETED");
            progress.setFileName(fileName);
            progress.setTimestamp(LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/redirect-progress/" + operationId, progress);
            
            // Общее уведомление
            notificationService.sendGeneralNotification(
                String.format("Обработка редиректов завершена. Обработано %d URLs. Файл: %s", 
                    processedCount, fileName), 
                NotificationDto.NotificationType.SUCCESS
            );
            
        } catch (Exception e) {
            log.error("Ошибка отправки уведомления о завершении для операции {}", operationId, e);
        }
    }
    
    private void sendErrorNotification(String operationId, String errorMessage) {
        try {
            RedirectProgressDto progress = new RedirectProgressDto();
            progress.setOperationId(operationId);
            progress.setMessage(errorMessage);
            progress.setStatus("ERROR");
            progress.setTimestamp(LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/redirect-progress/" + operationId, progress);
            
            // Общее уведомление
            notificationService.sendGeneralNotification(
                "Ошибка обработки редиректов: " + errorMessage, 
                NotificationDto.NotificationType.ERROR
            );
            
        } catch (Exception e) {
            log.error("Ошибка отправки уведомления об ошибке для операции {}", operationId, e);
        }
    }
}