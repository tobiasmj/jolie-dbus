include "DBus.iol"
include "console.iol"
include "dbusTests.iol"

outputPort dk.itu.TypeTest {
	Location: "localsocket:/tmp/launch-mBASPs/unix_domain_listener"
//	Location: "localsocket:/tmp/dbus-test"
	Protocol: dbus {
		.debug=true
//		.object_path="/test/path"	
	}
	MessageBus: true
	Interfaces: dk.itu.TypeTest,org.freedesktop.DBus
}

main
{
    	install( Error=>
        	println@Console( )()
    	);

	// Testing faulty method
//	Hello@dk.itu.TypeTest()(response);

	// invoking simple test service

	SimpleType.byte = 8B;
	SimpleType.boolean = true;
	SimpleType.int16 = 16S;
	SimpleType.uint16 = 16US;
	SimpleType.int32 = 32;
	SimpleType.uint32 = 32U;
	SimpleType.int64 = 64L;
	SimpleType.uint64 = 64UL;
	SimpleType.double = 1.2;
	SimpleType.string = "testString";
        while(i < 1){
        	TestSimpleTypes@dk.itu.TypeTest(SimpleType)(response);
                println@Console("Result of TestSimpleType @ dk.itu.TypeTest : " + response)();
                i++
        };
	// invoking simple array test service
	ArrayType.byte[0] = 0B; 
	ArrayType.byte[1] = 1B;
	ArrayType.byte[2] = 2B;

	ArrayType.boolean[0] = false; 
	ArrayType.boolean[1] = true;
	ArrayType.boolean[2] = false;

	ArrayType.int16[0] = 0S; 
	ArrayType.int16[1] = 1S;
	ArrayType.int16[2] = 2S;

	ArrayType.uint16[0] = 0US; 
	ArrayType.uint16[1] = 1US;
	ArrayType.uint16[2] = 2US;

	ArrayType.int32[0] = 0; 
	ArrayType.int32[1] = 1;
	ArrayType.int32[2] = 2;

	ArrayType.uint32[0] = 0U; 
	ArrayType.uint32[1] = 1U;
	ArrayType.uint32[2] = 2U;

	ArrayType.int64[0] = 0L; 
	ArrayType.int64[1] = 1L;
	ArrayType.int64[2] = 2L;

	ArrayType.uint64[0] = 0UL; 
	ArrayType.uint64[1] = 1UL;
	ArrayType.uint64[2] = 2UL;

	ArrayType.double[0] = 0.0; 
	ArrayType.double[1] = 1.0;
	ArrayType.double[2] = 2.0;

	ArrayType.string[0] = "test"; 
	ArrayType.string[1] = "string";
	ArrayType.string[2] = "testString";

	TestArrays@dk.itu.TypeTest(ArrayType)(response);
	println@Console("Result of TestArray @ dk.itu.TypeTest : " + response)();

	// invoking struct test

	StructType.struct[0].name = "0outerStruct";
	StructType.struct[0].innerStruct.name = "0innerStruct";
	StructType.struct[1].name = "1outerStruct";
	StructType.struct[1].innerStruct.name = "1innerStruct";
	TestStruct@dk.itu.TypeTest(StructType)(response);
	println@Console("Result of TestStruct @ dk.itu.TypeTest : " + response)();

	// emitting signal
	TestSignal@dk.itu.TypeTest();

	// testing Dicts
	DictEntryType.entry[0].key = "testKey0";
	DictEntryType.entry[0].value = 0;
	DictEntryType.entry[1].key = "testKey1";
	DictEntryType.entry[1].value = 1;
	TestDictEntry@dk.itu.TypeTest(DictEntryType)(response);
	println@Console("Result of TestDictEntry @ dk.itu.TypeTest : " + response)();

	// testing variant/any
	TestVariant@dk.itu.TypeTest(0)(response);
	println@Console("Result of TestVariant with int @ dk.itu.TypeTest : " + response)();
	TestVariant@dk.itu.TypeTest(0U)(response);
	println@Console("Result of TestVariant with uint32 @ dk.itu.TypeTest : " + response)()
}
