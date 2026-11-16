package com.tatakai.manager.catalog.dto.response;

import com.tatakai.manager.catalog.entity.CatalogItemKind;

/** Item que tem imagem; scope = GLOBAL ou CAMPAIGN (nulo na listagem global). */
public record CatalogItemImageResponse(CatalogItemKind kind, String key, String scope) {}
