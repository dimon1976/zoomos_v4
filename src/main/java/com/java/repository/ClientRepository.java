package com.java.repository;

import com.java.model.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    // Поиск клиента по имени (без учета регистра)
    Optional<Client> findByNameIgnoreCase(String name);

    // Проверка существования клиента с таким именем
    boolean existsByNameIgnoreCase(String name);

    // Поиск клиентов, имя которых содержит указанную строку
    List<Client> findByNameContainingIgnoreCase(String namePart);

    // Найти клиентов с файловыми операциями
    @Query("SELECT DISTINCT c FROM Client c JOIN c.fileOperations")
    List<Client> findClientsWithFileOperations();

    // Получить список клиентов с количеством файловых операций
    @Query("SELECT c, COUNT(fo) FROM Client c LEFT JOIN c.fileOperations fo GROUP BY c")
    List<Object[]> findClientsWithFileOperationCount();
}
