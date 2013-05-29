include "console.iol"
include "dbusTests.iol"
include "Dbus.iol"
execution { concurrent }
inputPort dk.itu.TypeTest {
	Location: "localsocket:/tmp/launch-mBASPs/unix_domain_listener"
//        Location: "localsocket:/tmp/dbus-test"
	Protocol: dbus {
		.debug=true;
		.matchRules[0] = "eavesdrop=true,type='signal',interface='dk.itu.TypeTest'"
//		.introspect = false
	}
	MessageBus: true
	Interfaces: dk.itu.TypeTest
}
outputPort signals {
	Location: "localsocket:/tmp/launch-mBASPs/unix_domain_listener"
	Protocol: dbus {
		.debug=true
	}
	MessageBus: true
	Interfaces: dk.itu.TypeTest.Signals
}
init{
	TestSignal@signals()
}
main
{
	[TestSignal(void)]{
		println@Console("Recieved TestSignal signal")()

	}
// testing instanceof //
	[TestSimpleTypes(SimpleTypes)(out){
		out = true;
		if( SimpleTypes.byte instanceof byte) { println@Console("instanceof byte : success")() } else { println@Console("instanceof byte : fail")() ; out = false};
		if( SimpleTypes.boolean instanceof bool) { println@Console("instanceof bool : success")() } else { println@Console("instanceof boolean : fail")() ; out = false};
		if( SimpleTypes.int16 instanceof int16) { println@Console("instanceof int16 : success")() } else { println@Console("instanceof int16 : fail")() ; out = false};
		if( SimpleTypes.uint16 instanceof uint16) { println@Console("instanceof uint16 : success")() } else { println@Console("instanceof uint16 : fail")() ; out = false};
		if( SimpleTypes.int32 instanceof int) { println@Console("instanceof int32 : success")() } else { println@Console("instanceof int32 : fail")() ; out = false};
		if( SimpleTypes.uint32 instanceof uint32) { println@Console("instanceof uint32 : success")() } else { println@Console("instanceof uint32 : fail")() ; out = false};
		if( SimpleTypes.int64 instanceof long) { println@Console("instanceof int64 : success")() } else { println@Console("instanceof int64 : fail")() ; out = false};
		if( SimpleTypes.uint64 instanceof uint64) { println@Console("instanceof uint64 : success")() } else { println@Console("instanceof uint64 : fail")() ; out = false};
		if( SimpleTypes.double instanceof double) { println@Console("instanceof double : success")() } else {println@Console("instanceof double : fail")() ; out = false};
		if( SimpleTypes.string instanceof string) { println@Console("instanceof string : success")() } else { println@Console("instanceof string : fail")() ; out = false};

// Testing values //
		if( SimpleTypes.byte == 8B) { println@Console("value of byte : success (" + SimpleTypes.byte + ")")() } else {  println@Console("value of byte : fail (" + SimpleTypes.string + ")")(); out = false};
		if( SimpleTypes.boolean == true) { println@Console("value of boolean : success (" + SimpleTypes.boolean + ")")() } else {  println@Console("value of boolean : fail (" + SimpleTypes.string + ")")(); out = false};
		if( SimpleTypes.int16 == 16S ) { println@Console("value of int16 : success (" + SimpleTypes.int16 + ")")() } else {  println@Console("value of int16 : fail (" + SimpleTypes.string + ")")(); out = false};
		if( SimpleTypes.uint16 == 16US ) { println@Console("value of uint16 : success (" + SimpleTypes.uint16 + ")")() } else {  println@Console("value of uint16 : fail (" + SimpleTypes.string + ")")(); out = false};
		if( SimpleTypes.int32 == 32 ) { println@Console("value of int32 : success (" + SimpleTypes.int32 + ")")() } else {  println@Console("value of int32 : fail (" + SimpleTypes.string + ")")(); out = false};
		if( SimpleTypes.uint32 == 32U ) { println@Console("value of uint32 : success (" + SimpleTypes.uint32 + ")")() } else {  println@Console("value of uint32 : fail (" + SimpleTypes.string + ")")(); out = false};
		if( SimpleTypes.int64 == 64L ) { println@Console( "value of int64 : success (" + SimpleTypes.int64 + ")")() } else {  println@Console("value of int64 : fail (" + SimpleTypes.string + ")")(); out = false};
		if( SimpleTypes.uint64 == 64UL ) { println@Console("value of uint64 : success (" + SimpleTypes.uint64 + ")")() } else {  println@Console("value of uint64 : fail (" + SimpleTypes.string + ")")(); out = false};
		if( SimpleTypes.double == 1.2 ) { println@Console("value of double : success (" + SimpleTypes.double + ")")() } else {  println@Console("value of double : fail (" + SimpleTypes.string + ")")(); out = false};
		if( SimpleTypes.string == "testString" ) { println@Console("value of string : success (" + SimpleTypes.string + ")")() } else {  println@Console("value of string : fail (" + SimpleTypes.string + ")")(); out = false}
	}]{nullProcess}
        [TestArrays(ArrayTypes)(out){
		if(ArrayTypes.byte[0] == 0B && ArrayTypes.byte[1] == 1B && ArrayTypes.byte[2] = 2B) {
			success[0].value = true;
		 	success[0].type ="byte"
		} else {
			success[0].value = false;
			success[0].type = "byte"
		};
		if(ArrayTypes.boolean[0] == false && ArrayTypes.boolean[1] == true && ArrayTypes.boolean[2] = false) {
			success[1].value = true;
			success[1].type = "bool"
		} else {
			success[1].value = false;
			success[1].type ="bool"
		};
		if(ArrayTypes.int16[0] == 0S && ArrayTypes.int16[1] == 1S && ArrayTypes.int16[2] = 2S) {
			success[2].value = true;
			success[2].type = "int16" 
		} else {
			success[2].value = false;
			success[2].type = "int16"
		};
		if(ArrayTypes.uint16[0] == 0US && ArrayTypes.uint16[1] == 1US && ArrayTypes.uint16[2] = 2US) {
			success[3].value = true;
			success[3].type = "uint16" 
		} else {
			success[3].value = false;
			success[3].type = "uint16"
		};
		if(ArrayTypes.int32[0] == 0 && ArrayTypes.int32[1] == 1 && ArrayTypes.int32[2] = 2) {
			success[4].value = true;
			success[4].type = "int32" 
		} else {
			success[4].value = false;
			success[4].type = "int32"
		};
		if(ArrayTypes.uint32[0] == 0U && ArrayTypes.uint32[1] == 1U && ArrayTypes.uint32[2] = 2U) {
			success[5].value = true;
			success[5].type = "uint32" 
		} else {
			success[5].value = false;
			success[5].type = "uint32"
		};
		if(ArrayTypes.int64[0] == 0L && ArrayTypes.int64[1] == 1L && ArrayTypes.int64[2] = 2L) {
			success[6].value = true;
			success[6].type = "int64" 
		} else {
			success[6].value = false;
			success[6].type = "int64"
		};
		if(ArrayTypes.uint64[0] == 0UL && ArrayTypes.uint64[1] == 1UL && ArrayTypes.uint64[2] = 2UL) {
			success[7].value = true;
			success[7].type = "uint64" 
		} else {
			success[7].value = false;
			success[7].type = "uint64"
		};
		if(ArrayTypes.double[0] == 0.0 && ArrayTypes.double[1] == 1.0 && ArrayTypes.double[2] = 2.0) { 
			success[8].value = true;
		 	success[8].type = "double" 
		} else { 
			success[8].value = false;
			success[8].type = "double"
		};
		if(ArrayTypes.string[0] == "test" && ArrayTypes.string[1] == "string" && ArrayTypes.string[2] = "testString") { 
			success[9].value = true; 
			success[9].type = "string" 
		} else { 
			success[9].value = false;
			success[9].type = "string"
		};
		out = true;
		for(i = 0, i < 10, i++) {
			if(success[i] = false){
				println@Console("ArrayTest failed @ " + success[i].type)();
				out = false
			}
		}
        }]{nullProcess}
        [TestDictEntry(DictEntryType)(out){
                if(DictEntryType.entry[0].key == "testKey0" && DictEntryType.entry[0].value == 0 
		&& DictEntryType.entry[1].key == "testKey1" && DictEntryType.entry[1].value == 1) {
			out = true
		} else {
			out = false
		}
        }]{nullProcess}
	[TestStruct(StructType)(out){
		if(StructType.struct[0].name == "0outerStruct" && StructType.struct[0].innerStruct.name == "0innerStruct"
		&& StructType.struct[1].name == "1outerStruct" && StructType.struct[1].innerStruct.name == "1innerStruct") {
			out = true
		} else {
			out = false
		}
	}]{nullProcess}

	[TestVariant(any)(out){
		out = true
	}]{nullProcess}
}
