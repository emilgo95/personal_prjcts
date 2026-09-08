-- ===========================================================================
-- Vínculos entre puntos de control y normativa aplicable
-- ===========================================================================

INSERT INTO control_point_regulations (control_point_id, regulation_id)
SELECT cp.id, r.id
FROM control_points cp
JOIN standard_chapters ch ON ch.id = cp.chapter_id
JOIN audit_standards s ON s.id = ch.standard_id
JOIN (VALUES
 ('GLOBALGAP_IFA_V6','AF 2.1','EU_178_2002'),
 ('GLOBALGAP_IFA_V6','AF 2.1','ISO_22005'),
 ('GLOBALGAP_IFA_V6','AF 2.5','GLOBALGAP_GR'),
 ('GLOBALGAP_IFA_V6','AF 2.6','GLOBALGAP_GR'),
 ('GLOBALGAP_IFA_V6','CB 4.1','EU_DIR_91_676'),
 ('GLOBALGAP_IFA_V6','CB 5.1','EU_DIR_2000_60'),
 ('GLOBALGAP_IFA_V6','CB 7.1','EU_DIR_2009_128'),
 ('GLOBALGAP_IFA_V6','CB 7.2','EU_1107_2009'),
 ('GLOBALGAP_IFA_V6','CB 7.4','EU_396_2005'),
 ('GLOBALGAP_IFA_V6','CB 7.5','EU_DIR_2009_128'),
 ('GLOBALGAP_IFA_V6','FV 9.1','EU_852_2004'),
 ('GLOBALGAP_IFA_V6','FV 9.2','EU_852_2004'),
 ('GLOBALGAP_IFA_V6','FV 9.3','EU_852_2004'),
 ('GLOBALGAP_IFA_V6','FV 9.3','US_FSMA_PSR'),
 ('GLOBALGAP_IFA_V6','FV 9.5','EU_396_2005'),
 ('GLOBALGAP_IFA_V6','FV 8.1','US_FSMA_PSR'),
 ('GLOBALGAP_IFA_V6','AF 10.1','EU_2017_625'),
 ('GRASP_V2','G 4.1','ILO_C138'),
 ('GRASP_V2','G 4.1','ILO_C182'),
 ('GRASP_V2','G 4.2','ILO_C182'),
 ('GRASP_V2','G 6.1','ILO_C100'),
 ('GRASP_V2','G 8.1','ILO_C111'),
 ('GRASP_V2','G 8.2','ILO_C087'),
 ('GRASP_V2','G 8.2','ILO_C098'),
 ('SMETA_6_1','0.A','ETI_BASE_CODE'),
 ('SMETA_6_1','1.A','ILO_C029'),
 ('SMETA_6_1','1.A','ILO_C105'),
 ('SMETA_6_1','1.B','ILO_C138'),
 ('SMETA_6_1','1.B','ILO_C182'),
 ('SMETA_6_1','1.C','ILO_C087'),
 ('SMETA_6_1','1.C','ILO_C098'),
 ('SMETA_6_1','1.D','ILO_C100'),
 ('SMETA_6_1','1.F','ILO_C111'),
 ('SMETA_6_1','0.C','UNGP_BHR'),
 ('SMETA_6_1','3.A','EU_2017_625'),
 ('SMETA_6_1','3.D','EU_396_2005'),
 ('SMETA_6_1','3.D','EU_DIR_91_676')
) AS m(standard_code, ref, regulation_code)
  ON m.standard_code = s.code AND m.ref = cp.ref
JOIN regulations r ON r.code = m.regulation_code;
