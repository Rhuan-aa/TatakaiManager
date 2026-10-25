package com.tatakai.manager.martial.dto.response;

import java.util.List;

public record SheetMartialResponse(List<CharacterArtResponse> arts, List<CharacterTechniqueResponse> techniques) {}
