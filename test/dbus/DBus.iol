type EnvironmentDict:void{
	.entry*:void{
		.arg1_key:string
		.arg2_value:string
	}
}
type NamesResponse:void{
	.name*:string
}

type RequestName:void{
	.arg2_flag:uint32
	.arg1_name:string
}

type NameOwnerChanged:void{
	.arg1_nameWithNewOwner:string
	.arg2_oldOwner:string
	.arg3_newOwner:string
}

type ServiceName:void{
	.arg1_nameOfService:string
	.arg2_flags:uint32	
}

interface org.freedesktop.DBus {
RequestResponse:
	GetConnectionUnixProcessID(string)(int),
	GetConnectionUnixUser(string)(int),
	GetId(string)(string),
	GetNameOwner(string)(string),
	Hello(void)(string),
	ListActivatableNames(void)(NamesResponse),
	ListNames(void)(NamesResponse),
	ListQueuedWoners(string)(NamesResponse),
	NameHasOwner(string)(bool),
	RequestName(RequestName)(uint32),
	ReleaseName(string)(uint32),
OneWay:
	AddMatch(string),
	RemoveMatch(string),
	NameAquired(string),
        NameOwnerChanged(NameOwnerChanged),
        NameLost(string),
	StartServiceByName(ServiceName),
	UpdateActivationEnvironment(EnvironmentDict),
}
