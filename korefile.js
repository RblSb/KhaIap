let project = new Project('iap');

project.addFile('cpp/iap/**');
project.addIncludeDir('cpp/iap');
project.addJavaDir('java');
const android = project.targetOptions.android;
android.permissions = [
	'com.android.vending.BILLING'
];

resolve(project);
