package com.java.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDto {

    private Long id;

    @NotBlank(message = "Имя клиента не может быть пустым")
    @Size(min = 2, max = 255, message = "Имя клиента должно содержать от 2 до 255 символов")
    private String name;

    private String description;

    @Email(message = "Неверный формат email")
    private String contactEmail;

    private String contactPhone;

    // Количество файловых операций (для отображения в списке)
    private Integer fileOperationsCount;
}