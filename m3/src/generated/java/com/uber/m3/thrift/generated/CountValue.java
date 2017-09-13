/**
 * Autogenerated by Thrift Compiler (0.9.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.uber.m3.thrift.generated;

import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;

import org.apache.thrift.scheme.TupleScheme;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.Collections;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Different types of count values
 */
public class CountValue implements org.apache.thrift.TBase<CountValue, CountValue._Fields>, java.io.Serializable, Cloneable {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("CountValue");

  private static final org.apache.thrift.protocol.TField I64_VALUE_FIELD_DESC = new org.apache.thrift.protocol.TField("i64Value", org.apache.thrift.protocol.TType.I64, (short)1);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new CountValueStandardSchemeFactory());
    schemes.put(TupleScheme.class, new CountValueTupleSchemeFactory());
  }

  public long i64Value; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    I64_VALUE((short)1, "i64Value");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // I64_VALUE
          return I64_VALUE;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final int __I64VALUE_ISSET_ID = 0;
  private byte __isset_bitfield = 0;
  private _Fields optionals[] = {_Fields.I64_VALUE};
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.I64_VALUE, new org.apache.thrift.meta_data.FieldMetaData("i64Value", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(CountValue.class, metaDataMap);
  }

  public CountValue() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public CountValue(CountValue other) {
    __isset_bitfield = other.__isset_bitfield;
    this.i64Value = other.i64Value;
  }

  public CountValue deepCopy() {
    return new CountValue(this);
  }

  @Override
  public void clear() {
    setI64ValueIsSet(false);
    this.i64Value = 0;
  }

  public long getI64Value() {
    return this.i64Value;
  }

  public CountValue setI64Value(long i64Value) {
    this.i64Value = i64Value;
    setI64ValueIsSet(true);
    return this;
  }

  public void unsetI64Value() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __I64VALUE_ISSET_ID);
  }

  /** Returns true if field i64Value is set (has been assigned a value) and false otherwise */
  public boolean isSetI64Value() {
    return EncodingUtils.testBit(__isset_bitfield, __I64VALUE_ISSET_ID);
  }

  public void setI64ValueIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __I64VALUE_ISSET_ID, value);
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case I64_VALUE:
      if (value == null) {
        unsetI64Value();
      } else {
        setI64Value((Long)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case I64_VALUE:
      return Long.valueOf(getI64Value());

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case I64_VALUE:
      return isSetI64Value();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof CountValue)
      return this.equals((CountValue)that);
    return false;
  }

  public boolean equals(CountValue that) {
    if (that == null)
      return false;

    boolean this_present_i64Value = true && this.isSetI64Value();
    boolean that_present_i64Value = true && that.isSetI64Value();
    if (this_present_i64Value || that_present_i64Value) {
      if (!(this_present_i64Value && that_present_i64Value))
        return false;
      if (this.i64Value != that.i64Value)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  public int compareTo(CountValue other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;
    CountValue typedOther = (CountValue)other;

    lastComparison = Boolean.valueOf(isSetI64Value()).compareTo(typedOther.isSetI64Value());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetI64Value()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.i64Value, typedOther.i64Value);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("CountValue(");
    boolean first = true;

    if (isSetI64Value()) {
      sb.append("i64Value:");
      sb.append(this.i64Value);
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class CountValueStandardSchemeFactory implements SchemeFactory {
    public CountValueStandardScheme getScheme() {
      return new CountValueStandardScheme();
    }
  }

  private static class CountValueStandardScheme extends StandardScheme<CountValue> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, CountValue struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // I64_VALUE
            if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
              struct.i64Value = iprot.readI64();
              struct.setI64ValueIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, CountValue struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.isSetI64Value()) {
        oprot.writeFieldBegin(I64_VALUE_FIELD_DESC);
        oprot.writeI64(struct.i64Value);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class CountValueTupleSchemeFactory implements SchemeFactory {
    public CountValueTupleScheme getScheme() {
      return new CountValueTupleScheme();
    }
  }

  private static class CountValueTupleScheme extends TupleScheme<CountValue> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, CountValue struct) throws org.apache.thrift.TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetI64Value()) {
        optionals.set(0);
      }
      oprot.writeBitSet(optionals, 1);
      if (struct.isSetI64Value()) {
        oprot.writeI64(struct.i64Value);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, CountValue struct) throws org.apache.thrift.TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(1);
      if (incoming.get(0)) {
        struct.i64Value = iprot.readI64();
        struct.setI64ValueIsSet(true);
      }
    }
  }

}

