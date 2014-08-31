package sneer.impl.simulator;

import sneer.*;
import sneer.admin.*;
import sneer.commons.*;

public class SneerAdminSimulator implements SneerAdmin, sneer.keys.Keys {
	
	private final PrivateKey neidePrik = Keys.createPrivateKey();

	private final SneerSimulator sneer = new SneerSimulator(neidePrik);

	{
		SystemReport.updateReport("simulator.start");
	}
	
	
	@Override
	public Sneer sneer() {
		return sneer;
	}
	
	
	@Override
	public PrivateKey privateKey() {
		return neidePrik;
	}


	@Override
	public sneer.keys.Keys keys() {
		return this;
	}


	@Override
	public PublicKey createPublicKey(String bytesAsString) {
		return Keys.createPublicKey(bytesAsString);
	}

}