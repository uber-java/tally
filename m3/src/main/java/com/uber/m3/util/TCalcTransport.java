package com.uber.m3.thrift.transport;

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

/**
 * Created by sliu on 12/23/15.
 * for counting, very much not thread safe
 */
public class TCalcTransport extends TTransport {

  private int count = 0;
  @Override
  public boolean isOpen() {
    return true;
  }

  @Override
  public void open() throws TTransportException {
  }

  @Override
  public void close() {
  }

  @Override
  public int read(byte[] bytes, int i, int i1) throws TTransportException {
    return 0;
  }

  @Override
  public void write(byte[] bytes, int i, int len) throws TTransportException {
    this.count += len;
  }

  public int getWrittenCount() {
    int tmp = this.count;
    this.count = 0;
    return tmp;
  }
}
