package com.java.service.client;

import com.java.dto.ClientDto;
import com.java.model.Client;

import java.util.List;
import java.util.Optional;

public interface ClientService {

    List<ClientDto> getAllClients();

    Optional<ClientDto> getClientById(Long id);

    Optional<ClientDto> getClientByName(String name);

    ClientDto createClient(ClientDto clientDto);

    ClientDto updateClient(Long id, ClientDto clientDto);

    boolean deleteClient(Long id);

    List<ClientDto> searchClients(String namePart);

    Optional<Client> findClientEntityById(Long id);

    void toggleActive(Long id);

    void reorder(List<Long> orderedIds);
}
