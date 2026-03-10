package biologics.adapter.namegen;

import java.util.Map;

import genedata.bx.adapter.NameGenerator;
import genedata.bx.adapter.NumberingFactory;

public class ContextBasedAntigenExample implements NameGenerator {

	private static final long serialVersionUID = 1L;
	public static final String	delimiter			= ",";
	public static final String	escapedDelimiter	= "%2C";
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
	}

	@Override
	public String prefillName(Map<String, String> context) {
		return null;
	}

	@Override
	public boolean showNameToUserForEdit() {
		return true;
	}

	@Override
	public String generateName(Map<String, String> context, NumberingFactory numbering) {
		String alias = context.get("ALIAS");
		if (alias != null && alias.trim().length() > 0) {
			return alias.trim();
		}
		String relatedEntityAlias = context.get("RELATED_ENTITY_ALIAS");
		String relatedEntityType = context.get("RELATED_ENTITY_TYPE");
		if (relatedEntityAlias != null && relatedEntityAlias.trim().length() > 0 && "Ppt".equals(relatedEntityType)) {
			return unescapeContextName(relatedEntityAlias);
		}
		
		// handle standard use cases
		return "@DEFAULT@";	
	}
	
	public static String unescapeContextName(String orig) {
		return orig == null ? null : orig.replace(escapedDelimiter, delimiter);
	}
}
