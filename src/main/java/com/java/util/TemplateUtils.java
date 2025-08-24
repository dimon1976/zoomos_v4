package com.java.util;

import com.java.model.Client;
import com.java.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TemplateUtils {

    private final ClientRepository clientRepository;

    public Client getClientById(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));
    }

    public void validateTemplateNameUniqueness(String templateName, Client client, 
                                             boolean nameExists) {
        if (nameExists) {
            throw new IllegalArgumentException("Шаблон с таким именем уже существует");
        }
    }

    public void validateTemplateOwnership(Long templateClientId, Long expectedClientId) {
        if (!templateClientId.equals(expectedClientId)) {
            throw new IllegalArgumentException("Нельзя изменить клиента шаблона");
        }
    }
}