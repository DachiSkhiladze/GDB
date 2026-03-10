package biologics.adapter.fastahp.isolate;

import genedata.bx.adapter.IsolateBasedFastaHeaderParser;
import genedata.bx.adapter.assay.Isolate;
import genedata.bx.adapter.assay.IsolateInformationProvider;
import genedata.bx.adapter.plate.Plate;
import biologics.adapter.fastahp.AbstractFastaHeaderParser;

import java.util.List;

public abstract class AbstractIsolatedBasedFastaHeaderParser extends AbstractFastaHeaderParser implements IsolateBasedFastaHeaderParser{

//	private static Logger log = Logger.getLogger(AbstractFastaHeaderParser.class);

	private IsolateInformationProvider isolateInformationProvider;

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.IsolateBasedFastaHeaderParser#handlesPlateBasedIsolates()
	 */
	@Override
	public boolean handlesPlateBasedIsolates() {
		// override for plate-based adapter
		return false;
	}

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.IsolateBasedFastaHeaderParser#handlesPlateFreeIsolates()
	 */
	@Override
	public boolean handlesPlateFreeIsolates() {
		// override for plate-free adapter
		return false;
	}

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.IsolateBasedFastaHeaderParser#getIdentifiedIsolate()
	 */
	@Override
	public 	Isolate getIdentifiedIsolate() {
		return null;
	}

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.IsolateBasedFastaHeaderParser#getIdentifiedPlate()
	 */
	@Override
	public Plate getIdentifiedPlate() {
		return null;
	}

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.IsolateBasedFastaHeaderParser#getIdentifiedWellColumn()
	 */
	@Override
	public int getIdentifiedWellColumn() {
		return -1;
	}

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.IsolateBasedFastaHeaderParser#getIdentifiedWellRow()
	 */
	@Override
	public int getIdentifiedWellRow() {
		return -1;
	}

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.IsolateBasedFastaHeaderParser#setIsolateInformationProvider(genedata.bx.adapter.assay.IsolateInformationProvider)
	 */
	@Override
	public void setIsolateInformationProvider(IsolateInformationProvider isolateInformationProvider) {
		this.isolateInformationProvider = isolateInformationProvider;
	}

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.IsolateBasedFastaHeaderParser#setPlates(java.util.List)
	 */
	@Override
	public void setPlates(List<Plate> plates) {
		// empty
	}

	/**
	 * @param valueString The identifier string or name of an isolate.
	 * @return The matching isolate, or {@code null} if not found.
	 */
	protected Isolate getIsolate(String valueString) {
		return isolateInformationProvider.retrieveIsolate(valueString);
	}

}
