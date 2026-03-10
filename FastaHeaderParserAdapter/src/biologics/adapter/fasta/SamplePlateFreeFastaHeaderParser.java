package biologics.adapter.fasta;

import genedata.bx.adapter.entity.Isolate;
import genedata.bx.adapter.entity.IsolateInformationProvider;
import genedata.bx.adapter.fasta.v2.FastaCallback;
import genedata.bx.adapter.fasta.v2.FastaException;
import genedata.bx.adapter.fasta.v2.FastaHeaderParser;
import genedata.bx.adapter.fasta.v2.FastaHeaderParser.Context;
import genedata.bx.adapter.fasta.v2.FastaHeaderParser.Supports;
import genedata.bx.adapter.fasta.v2.FastaOptions;
import genedata.bx.adapter.fasta.v2.HeaderRecord;

import java.util.Map;

/**
 * Sample implementation of FastaHeaderParser that identifies
 * Isolates based on their ID.
 * <br>
 * It processes input read from a fasta-formatted file like the following:
 * <pre>
>CL-37/392/H
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
>CL-105/403/H
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
>CL-99/400/L
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
>CL-111/400/S
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
</pre>
 * Besides the qualified ID of the Isolate ("CL-37"), it also contains a quality score ('392'),
 * and the chain ('H', 'L', 'S') that was sequenced. 
 */
@Supports(Context.PLATE_FREE_ISOLATES)
final public class SamplePlateFreeFastaHeaderParser implements FastaHeaderParser {

	private FastaCallback callback;
	/**
	 * line being parsed
	 */
	private String line;
	
	/**
	 * record filled with values from parsing
	 */
	private HeaderRecord record;
	
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// no configuration needed in this example
	}

	@Override
	public void options(FastaOptions options) {
		// no options set in this example
	}

	@Override
	public HeaderRecord perform(FastaCallback callback) throws FastaException {
		this.callback= callback;

		line = callback.getHeaderLine();
		record = callback.createRecord();

		parse();
		return record;
	}

	/**
	 * Parse line and store values in record.
	 * Sample line (note: no '>' at the beginning):
	 * P001|B02|392|H
	 */
	private void parse() {
		String[] fields = line.split("[/]");
		int n = fields.length;
		if (n != 3) {
			reportError("Expected 3 fields but found " + n);
			return;
		}
		
		evaluateIsolate( fields[0]);
		evaluateQualityScore( fields[1]);
		evaluateChain( fields[2]);
	}


	/**
	 * Evaluate the Isolate ID 
	 * @param isolateStr
	 */
	private void evaluateIsolate(String isolateStr) {
		IsolateInformationProvider isolateInformationProvider = callback.getIsolateInformationProvider();
		Isolate isolate = isolateInformationProvider.retrieve(isolateStr);
		if (isolate == null) {
			// isolates might be called something else in the UI, e.g., "AntibodyClone"
			// make sure to report the problem using this label
			String uiLabelOfIsolate = isolateInformationProvider.getSingularEntityLabel();
			reportError("Could not find " + uiLabelOfIsolate + " " + isolateStr);
		}
		else {
			record.setIsolate(isolate);
		}
	}

	/**
	 * Evaluate a quality score, which is expected to be an integer
	 * @param qualityStr
	 */
	private void evaluateQualityScore(String qualityStr) {
		try {
			int score = Integer.parseInt(qualityStr);
			record.setQualityScore(score);
		} catch (NumberFormatException e) {
			reportError("Could not parse qualityScore: " + qualityStr);
		}	
	}

	/**
	 * Evaluate chain information
	 * @param chain
	 */
	@SuppressWarnings("deprecation")
	private void evaluateChain(String chain) {
		if ("H".equals(chain)) {
			record.setChainInfo( FastaCallback.HEAVY_CHAIN_KEY);
		}
		else if ("L".equals(chain)) {
			record.setChainInfo( FastaCallback.LIGHT_CHAIN_KEY);			
		}
		else if ("S".equals(chain)) {
			record.setChainInfo( FastaCallback.SCFV_KEY);			
		}
		else {
			reportError("Unknown chain: " + chain);
		}
	}

	/**
	 * Report an error together with the input line
	 * @param reason
	 */
	private void reportError(String reason) {
		callback.getReporter().error( "Error reading line |#0| : #1", line, reason);
	}

}
