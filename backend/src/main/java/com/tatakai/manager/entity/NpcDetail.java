package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entrada nomeada e descrita do NPC — reutilizada tanto para Conhecimentos quanto
 * para Traços (ambos com o mesmo formato {nome, descrição}). Embutido no documento
 * Mongo do NPC.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NpcDetail {

    private String name;
    private String description;
}
