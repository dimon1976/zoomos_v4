package com.java.service.client;

import com.java.dto.ClientDto;
import com.java.model.entity.Client;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления клиентами
 */
public interface ClientService {

    /**
     * Получить всех клиентов
     * @return список всех клиентов
     */
    List<ClientDto> getAllClients();

    /**
     * Найти клиента по ID
     * @param id идентификатор клиента
     * @return клиент или empty, если не найден
     */
    Optional<ClientDto> getClientById(Long id);

    /**
     * Найти клиента по имени (без учета регистра)
     * @param name имя клиента
     * @return клиент или empty, если не найден
     */
    Optional<ClientDto> getClientByName(String name);

    /**
     * Создать нового клиента
     * @param clientDto DTO с данными клиента
     * @return созданный клиент
     */
    ClientDto createClient(ClientDto clientDto);

    /**
     * Обновить данные клиента
     * @param id идентификатор клиента
     * @param clientDto DTO с новыми данными
     * @return обновленный клиент
     */
    ClientDto updateClient(Long id, ClientDto clientDto);

    /**
     * Удалить клиента
     * @param id идентификатор клиента
     * @return true, если клиент был удален, иначе false
     */
    boolean deleteClient(Long id);

    /**
     * Поиск клиентов по части имени
     * @param namePart часть имени для поиска
     * @return список найденных клиентов
     */
    List<ClientDto> searchClients(String namePart);

    /**
     * Найти Entity клиента по ID
     * @param id идентификатор клиента
     * @return Entity клиента или empty, если не найден
     */
    Optional<Client> findClientEntityById(Long id);
}