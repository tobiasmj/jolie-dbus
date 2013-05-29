type SimpleTypes: void {
	.byte:byte
	.boolean:bool
	.int16:int16
	.uint16:uint16
	.int32:int
	.uint32:uint32
	.int64:long
	.uint64:uint64
	.double:double
	.string:string
}
type ArrayTypes: void {
        .byte*:byte
        .boolean*:bool 
        .int16*:int16
        .uint16*:uint16
        .int32*:int
        .uint32*:uint32
        .int64*:long
        .uint64*:uint64
        .double*:double
        .string*:string
}


type DictEntryType: void {
	.entry*:void{
		.key:string
		.value:int
	}
}

type StructType: void {
	.struct*: void{
		.name:string
		.innerStruct:void{
			.name:string
		}
	}
}
interface dk.itu.TypeTest {

	RequestResponse:
		TestSimpleTypes(SimpleTypes)(bool),
		TestArrays(ArrayTypes)(bool),
		TestDictEntry(DictEntryType)(bool),
		TestStruct(StructType)(bool),
		TestVariant(any)(bool),
//		TestDepth(void)(bool),
	OneWay:
		TestSignal(void),
}

interface dk.itu.TypeTest.Signals {
	OneWay:
		TestSignal(void),
}
