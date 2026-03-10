package biologics.adapter.fasta;

import genedata.bx.adapter.entity.Plate;
import genedata.bx.adapter.entity.PlateInformationProvider;
import genedata.bx.adapter.entity.Well;
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
 * Isolates based on Plate name/barcode/qual-Id and well address.
 * <br>
 * It processes input read from a fasta-formatted file like the following:
 * <pre>
>P001|B02|392|H
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
>P001|A03|403|H
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
>P002|A07|400|L
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
>P002|B07|400|S
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
</pre>
 * Besides the plate name ('P001') and well address ('B02'), it also contains a quality score ('392'),
 * and the chain ('H', 'L', 'S') that was sequenced. 
 */
@Supports(Context.PLATE_BASED_ISOLATES) 
final public class SamplePlateBasedFastaHeaderParser implements FastaHeaderParser {
	
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
		String[] fields = line.split("[|]");
		int n = fields.length;
		if (n != 4) {
			reportError("Expected 4 fields but found " + n);
			return;
		}
		
		evaluatePlate( fields[0]);
		evaluateWell(  fields[1]);
		evaluateQualityScore( fields[2]);
		evaluateChain( fields[3]);
	}

	/**
	 * Plate is expected to exist in Biologics
	 * @param plateStr
	 */
	private void evaluatePlate(String plateStr) {
		PlateInformationProvider plateInformationProvider = callback.getPlateInformationProvider();
		Plate plate = plateInformationProvider.retrieve(plateStr); // alias or qualId or barcode
		if (plate == null) {
			// Plates might be called something else in the UI, e.g., "MTP"
			// make sure to report the problem using this label
			String uiLabelForPlate = plateInformationProvider.getSingularEntityLabel();
			reportError("Could not retrieve " + uiLabelForPlate + " : " + plateStr);
		}
		else {
			record.setPlate(plate);
		}
	}

	/**
	 * Evaluate well addresses of the form A01 .. H12
	 * @param wellAddressStr
	 */
	private void evaluateWell(String wellAddressStr) {
		try {
			Well well = callback.getWellAddressConverter().parseWellAddress(wellAddressStr);
			record.setWellRow(well.getRow());
			record.setWellColumn(well.getColumn());
		} catch (Throwable e) {
			reportError("Could not parse well address: " + wellAddressStr);
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
