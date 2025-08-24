package com.java.service.template;

import com.java.model.Client;
import com.java.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractTemplateService<T, D> {

    protected final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    protected Client getClientById(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент не найден"));
    }

    protected void validateTemplateOwnership(T template, Long clientId) {
        if (!getTemplateClientId(template).equals(clientId)) {
            throw new IllegalArgumentException("Шаблон не принадлежит клиенту");
        }
    }

    protected void validateUniqueTemplateName(String templateName, Client client, Long excludeTemplateId) {
        if (isTemplateNameExists(templateName, client, excludeTemplateId)) {
            throw new IllegalArgumentException("Шаблон с таким именем уже существует");
        }
    }

    protected abstract Long getTemplateClientId(T template);
    protected abstract boolean isTemplateNameExists(String templateName, Client client, Long excludeTemplateId);
}