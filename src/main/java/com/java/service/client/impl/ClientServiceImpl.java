package com.java.service.client.impl;

import com.java.model.Client;
import com.java.repository.ClientRepository;
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

    @Override
    @Transactional(readOnly = true)
    public List<ClientDto> getAllClients() {
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
        return clientRepository.findById(id)
                .map(client -> mapToDto(client, client.getFileOperations().size()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientDto> getClientByName(String name) {
        return clientRepository.findByNameIgnoreCase(name)
                .map(client -> mapToDto(client, client.getFileOperations().size()));
    }

    @Override
    @Transactional
    public ClientDto createClient(ClientDto clientDto) {
        if (clientRepository.existsByNameIgnoreCase(clientDto.getName())) {
            throw new IllegalArgumentException("Клиент с именем '" + clientDto.getName() + "' уже существует");
        }
        Client client = mapToEntity(clientDto);
        Client savedClient = clientRepository.save(client);
        log.info("Created new client with id: {}", savedClient.getId());
        return mapToDto(savedClient, 0);
    }

    @Override
    @Transactional
    public ClientDto updateClient(Long id, ClientDto clientDto) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Клиент с ID " + id + " не найден"));
        if (!client.getName().equalsIgnoreCase(clientDto.getName()) &&
                clientRepository.existsByNameIgnoreCase(clientDto.getName())) {
            throw new IllegalArgumentException("Клиент с именем '" + clientDto.getName() + "' уже существует");
        }
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
        if (!clientRepository.existsById(id)) {
            return false;
        }
        clientRepository.deleteById(id);
        log.info("Deleted client with id: {}", id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientDto> searchClients(String namePart) {
        List<Client> clients = clientRepository.findByNameContainingIgnoreCase(namePart);
        return clients.stream()
                .map(client -> mapToDto(client, client.getFileOperations().size()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Client> findClientEntityById(Long id) {
        return clientRepository.findById(id);
    }

    @Override
    @Transactional
    public void toggleActive(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Клиент " + id + " не найден"));
        client.setActive(!client.isActive());
        clientRepository.save(client);
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
    }

    private ClientDto mapToDto(Client client, Integer fileOperationsCount) {
        return ClientDto.builder()
                .id(client.getId())
                .name(client.getName())
                .description(client.getDescription())
                .regionCode(client.getRegionCode())
                .regionName(client.getRegionName())
                .isActive(client.isActive())
                .sortOrder(client.getSortOrder())
                .fileOperationsCount(fileOperationsCount)
                .build();
    }

    private Client mapToEntity(ClientDto dto) {
        return Client.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .regionCode(dto.getRegionCode())
                .regionName(dto.getRegionName())
                .build();
    }
}
