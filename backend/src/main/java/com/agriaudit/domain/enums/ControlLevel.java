package com.agriaudit.domain.enums;

/**
 * Nivel/obligatoriedad de un punto de control. El significado exacto depende del
 * estándar: GLOBALG.A.P. usa Major/Minor Must y Recommendation; Rainforest Alliance
 * usa Core e Improvement L1/L2.
 */
public enum ControlLevel {
    MAJOR_MUST,
    MINOR_MUST,
    RECOMMENDATION,
    CORE,
    IMPROVEMENT_L1,
    IMPROVEMENT_L2
}
