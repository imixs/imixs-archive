package org.imixs.archive.service.cassandra;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class DocumentSplitter implements Iterable<byte[]> {

	public int CHUNK_SIZE = 1048576; // 1mb

	private byte[] filedata = null;

	public DocumentSplitter(byte[] filedata) {
		super();
		this.filedata = filedata;
	}

	@Override
	public Iterator<byte[]> iterator() {

		return new ChunkIterator();

	}

	// Inner class to iterate the bytes in 2mb chunks
	private class ChunkIterator implements Iterator<byte[]> {
		private int cursor;
		private byte[] data;

		public ChunkIterator() {
			this.cursor = 0;
			// fetch the whole data in one array
			data = DocumentSplitter.this.filedata; // .getBytes();
		}

		public boolean hasNext() {
			return this.cursor < data.length;
		}

		public byte[] next() {
			if (this.hasNext()) {
				byte[] chunk;
				// check byte count from cursor...
				if (data.length > cursor + CHUNK_SIZE) {
					chunk = Arrays.copyOfRange(data, cursor, cursor + CHUNK_SIZE);
					cursor = cursor + CHUNK_SIZE;
				} else {
					// read last junk
					chunk = Arrays.copyOfRange(data, cursor, data.length);
					cursor = data.length;
				}
				return chunk;
			}
			throw new NoSuchElementException();
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
