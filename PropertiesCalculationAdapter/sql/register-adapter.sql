--
-- SQL statements for the registration of the Properties Calculation Adapter
--

-- Inactivate existing implementations of the Properties Calculation Adapter
update adapter_implementation set active = 'N' where adapter_interface_id in
  (select id from adapter_interface where java_classname like '%.PhysChemPropertiesCalculation');

-- Register the 'Mock Implementation' Properties Calculation Adapter
insert into adapter_implementation (ID,LABEL,ACTIVE,DESCRIPTION,JAVA_CLASSNAME,ADAPTER_INTERFACE_ID) values (
  adapter_implementation_seq.nextval,
  'Mock Implementation',
  'Y',
  'Mock adapter implementation for the calculation of physico-chemical properties.',
  'biologics.adapter.propertiescalculation.MockPhysChemPropertiesCalculation',
  (select id from adapter_interface where java_classname like '%.PhysChemPropertiesCalculation'));

-- end of file
