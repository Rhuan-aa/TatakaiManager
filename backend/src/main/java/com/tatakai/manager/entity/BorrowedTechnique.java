package com.tatakai.manager.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Técnica emprestada de outra arte (seção "Técnicas Emprestadas"): aprendida na graduação indicada desta arte. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BorrowedTechnique {
    /** Nome da arte de origem. */
    private String art;
    /** Nome completo da técnica na arte de origem. */
    private String name;
    private Graduation graduation;
}
