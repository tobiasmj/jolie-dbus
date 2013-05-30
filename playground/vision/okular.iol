interface org.kde.okular {
RequestResponse:
	goToPage(uint32)(void),
	openDocument(string)(void),
	currentPage(void)(uint32),
	currentDocument(void)(string)
}

interface org.kde.KApplication {
RequestResponse:
	quit(void)(void)
}
