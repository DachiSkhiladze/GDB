-- -----------------------------------------------------------------------------
-- Sample configuration 
-- -----------------------------------------------------------------------------

insert into adapter_implementation (id, adapter_interface_id, sort_order, label, description, java_classname)
values (
  adapter_implementation_seq.nextval, 
  (select id from adapter_interface where java_classname = 'genedata.bx.adapter.pp.platesetdesign.PpPlateSetDesignParserAdapter'),
  196,
  'Via Well Layout Map with TPP information', 
  'Reads a file containing well layout information to assign existing Target Product Proteins to the wells of Protein Production Plate Set Design Plates.', 
  'biologics.adapter.pp.platesetdesign.SamplePpPlateSetDesignParserAdapter'
);
