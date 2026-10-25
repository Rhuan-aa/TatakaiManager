package com.tatakai.manager.dto.response;

import java.util.List;

/** Resultado da importação: a ficha criada (PENDING_APPROVAL) e avisos de revalidação (F7). */
public record SheetImportResponse(CharacterSheetResponse sheet, List<String> warnings) {}
