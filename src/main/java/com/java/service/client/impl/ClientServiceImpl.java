package com.java.service.client.impl;

import com.java.model.Client;
import com.java.model.entity.ZoomosShop;
import com.java.repository.ClientRepository;
import com.java.repository.ZoomosShopRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.java.dto.ClientDto;
import com.java.service.client.ClientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final ZoomosShopRepository shopRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ClientDto> getAllClients() {
        log.debug("Getting all clients");
        List<Object[]> clientsWithCounts = clientRepository.findClientsWithFileOperationCount();

        return clientsWithCounts.stream()
                .map(result -> {
                    Client client = (Client) result[0];
                    Long count = (Long) result[1];
                    return mapToDto(client, count.intValue());
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientDto> getClientById(Long id) {
        log.debug("Getting client by id: {}", id);
        return clientRepository.findById(id)
                .map(client -> mapToDto(client, client.getFileOperations().size()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientDto> getClientByName(String name) {
        log.debug("Getting client by name: {}", name);
        return clientRepository.findByNameIgnoreCase(name)
                .map(client -> mapToDto(client, client.getFileOperations().size()));
    }

    @Override
    @Transactional
    public ClientDto createClient(ClientDto clientDto) {
        log.debug("Creating new client: {}", clientDto.getName());

        // Проверка существования клиента с таким именем
        if (clientRepository.existsByNameIgnoreCase(clientDto.getName())) {
            throw new IllegalArgumentException("Клиент с именем '" + clientDto.getName() + "' уже существует");
        }

        Client client = mapToEntity(clientDto);
        Client savedClient = clientRepository.save(client);

        // Автосвязка: если есть ZoomosShop с таким же именем — привязать
        shopRepository.findByShopNameIgnoreCase(savedClient.getName()).ifPresent(shop -> {
            shop.setClient(savedClient);
            shopRepository.save(shop);
            log.info("Auto-linked ZoomosShop '{}' to new client id={}", shop.getShopName(), savedClient.getId());
        });

        log.info("Created new client with id: {}", savedClient.getId());
        return mapToDto(savedClient, 0);
    }

    @Override
    @Transactional
    public ClientDto updateClient(Long id, ClientDto clientDto) {
        log.debug("Updating client with id: {}", id);

        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Клиент с ID " + id + " не найден"));

        // Проверка на дублирование имени при изменении
        if (!client.getName().equalsIgnoreCase(clientDto.getName()) &&
                clientRepository.existsByNameIgnoreCase(clientDto.getName())) {
            throw new IllegalArgumentException("Клиент с именем '" + clientDto.getName() + "' уже существует");
        }

        // Обновление полей
        client.setName(clientDto.getName());
        client.setDescription(clientDto.getDescription());
        client.setRegionCode(clientDto.getRegionCode());
        client.setRegionName(clientDto.getRegionName());


        Client updatedClient = clientRepository.save(client);
        log.info("Updated client with id: {}", id);

        return mapToDto(updatedClient, updatedClient.getFileOperations().size());
    }

    @Override
    @Transactional
    public boolean deleteClient(Long id) {
        log.debug("Deleting client with id: {}", id);

        if (!clientRepository.existsById(id)) {
            log.warn("Client with id: {} not found for deletion", id);
            return false;
        }

        clientRepository.deleteById(id);
        log.info("Deleted client with id: {}", id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientDto> searchClients(String namePart) {
        log.debug("Searching clients by name part: {}", namePart);

        List<Client> clients = clientRepository.findByNameContainingIgnoreCase(namePart);
        return clients.stream()
                .map(client -> mapToDto(client, client.getFileOperations().size()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Client> findClientEntityById(Long id) {
        log.debug("Finding client entity by id: {}", id);
        return clientRepository.findById(id);
    }

    @Override
    @Transactional
    public void linkShopToClient(Long clientId, Long shopId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Клиент " + clientId + " не найден"));
        ZoomosShop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Магазин " + shopId + " не найден"));
        shop.setClient(client);
        shopRepository.save(shop);
        log.info("Linked ZoomosShop id={} to client id={}", shopId, clientId);
    }

    @Override
    @Transactional
    public void unlinkShopFromClient(Long shopId) {
        shopRepository.findById(shopId).ifPresent(shop -> {
            shop.setClient(null);
            shopRepository.save(shop);
            log.info("Unlinked ZoomosShop id={} from client", shopId);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ZoomosShop> getLinkedShop(Long clientId) {
        return shopRepository.findByClientId(clientId);
    }

    @Override
    @Transactional
    public void toggleActive(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Клиент " + id + " не найден"));
        client.setActive(!client.isActive());
        clientRepository.save(client);
        log.info("Client id={} isActive={}", id, client.isActive());
    }

    @Override
    @Transactional
    public void reorder(List<Long> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            final int order = i;
            clientRepository.findById(orderedIds.get(i)).ifPresent(c -> {
                c.setSortOrder(order);
                clientRepository.save(c);
            });
        }
        log.info("Reordered {} clients", orderedIds.size());
    }

    // Маппер из entity в DTO
    private ClientDto mapToDto(Client client, Integer fileOperationsCount) {
        ClientDto.ClientDtoBuilder builder = ClientDto.builder()
                .id(client.getId())
                .name(client.getName())
                .description(client.getDescription())
                .regionCode(client.getRegionCode())
                .regionName(client.getRegionName())
                .isActive(client.isActive())
                .sortOrder(client.getSortOrder())
                .fileOperationsCount(fileOperationsCount);
        shopRepository.findByClientId(client.getId()).ifPresent(shop -> {
            builder.linkedShopId(shop.getId());
            builder.linkedShopName(shop.getShopName());
        });
        return builder.build();
    }

    // Маппер из DTO в entity
    private Client mapToEntity(ClientDto dto) {
        return Client.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .regionCode(dto.getRegionCode())
                .regionName(dto.getRegionName())
                .build();
    }
}