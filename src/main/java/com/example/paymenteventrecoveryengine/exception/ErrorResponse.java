package com.example.paymenteventrecoveryengine.exception;

import java.time.LocalDateTime;

public record ErrorResponse(String message,
                            int status,
                            LocalDateTime timestamp) {
}
