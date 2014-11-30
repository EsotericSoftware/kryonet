/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package com.esotericsoftware.kryonet.util;

import java.io.IOException;
import java.io.InputStream;

import com.esotericsoftware.kryonet.KryoNetException;

abstract public class InputStreamSender extends TcpIdleSender {
	private final InputStream input;
	private final byte[] chunk;

	public InputStreamSender (InputStream input, int chunkSize) {
		this.input = input;
		chunk = new byte[chunkSize];
	}

	protected final Object next () {
		try {
			int total = 0;
			while (total < chunk.length) {
				int count = input.read(chunk, total, chunk.length - total);
				if (count < 0) {
					if (total == 0) return null;
					byte[] partial = new byte[total];
					System.arraycopy(chunk, 0, partial, 0, total);
					return next(partial);
				}
				total += count;
			}
		} catch (IOException ex) {
			throw new KryoNetException(ex);
		}
		return next(chunk);
	}

	abstract protected Object next (byte[] chunk);
}
