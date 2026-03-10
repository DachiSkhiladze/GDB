-- Adapter_implementations needs to be registered with Biologics

-- (1) and (2) are mutually exclusive, you can register only one NameGenerator per entity

-- (1) SimplePptExample
insert into adapter_implementation
  (id,label,description,java_classname,adapter_interface_id)
values(
  adapter_implementation_seq.nextval,
  'Sample PPT Name Generator',
  'Sample implementation of Name Generator for PPT',
  'biologics.adapter.namegen.SimplePptExample',
  (select id from adapter_interface where java_classname = 'genedata.bx.adapter.NameGenerator')
);

update entity_configuration
	set name_generator_adapter_id = (select id from adapter_implementation where java_classname =  'biologics.adapter.namegen.SimplePptExample')
	where entity = 'Ppt';
	
-- (2) NumberingPptExample
insert into adapter_implementation
  (id,label,description,java_classname,adapter_interface_id)
values(
  adapter_implementation_seq.nextval,
  'Sample Numbering PPT Name Generator',
  'Sample implementation of Name Generator for PPT using NumberingFactory',
  'biologics.adapter.namegen.NumberingPptExample',
  (select id from adapter_interface where java_classname = 'genedata.bx.adapter.NameGenerator')
);

update entity_configuration
	set name_generator_adapter_id = (select id from adapter_implementation where java_classname =  'biologics.adapter.namegen.NumberingPptExample')
	where entity = 'Ppt';
	
-- (3) ContextBasedAntigenExample
insert into adapter_implementation
  (id,label,description,java_classname,adapter_interface_id)
values(
  adapter_implementation_seq.nextval,
  'Sample context-based Antigen Name Generator',
  'Sample implementation of Name Generator for Antigen using the information provided by the context',
  'biologics.adapter.namegen.ContextBasedAntigenExample',
  (select id from adapter_interface where java_classname = 'genedata.bx.adapter.NameGenerator')
);

update entity_configuration
	set name_generator_adapter_id = (select id from adapter_implementation where java_classname =  'biologics.adapter.namegen.ContextBasedAntigenExample')
	where entity = 'Antigen';
	
-- (4) NumberingAntigenBatchExample
insert into adapter_implementation
  (id,label,description,java_classname,adapter_interface_id)
values(
  adapter_implementation_seq.nextval,
  'Sample Numbering Antigen Batch Name Generator',
  'Sample implementation of Name Generator for Antigen Batch using NumberingFactory',
  'biologics.adapter.namegen.NumberingAntigenBatchExample',
  (select id from adapter_interface where java_classname = 'genedata.bx.adapter.NameGenerator')
);

update entity_configuration
	set name_generator_adapter_id = (select id from adapter_implementation where java_classname =  'biologics.adapter.namegen.NumberingAntigenBatchExample')
	where entity = 'AntigenBatch';
	
