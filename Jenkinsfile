pipeline {
 	agent none
 	tools {
		maven 'Maven 3.9.11' 
		jdk 'OpenJDK 25' 
	} 
	
	environment {
	    MAVEN_PROPERTIES_ID = "39194e51-be8a-4574-af3b-baed7a985ca1"
	    BUILD_PROPERTIES_ID = "14bec7ec-e689-43dc-9ed9-22767b144939"
	    BUILD_PROPERTIES_FILENAME = "bithatch.build.properties"
	}

	stages {
		stage ('TNFS Java') {
			parallel {
			    /*
                 * Deploy cross platform libraries
                 */
                stage ('Cross-platform TNFS Jar Libraries') {
                    agent {
                        label 'linux'
                    }
                    steps {
                        configFileProvider([
                                configFile(
                                    fileId: "${env.BUILD_PROPERTIES_ID}",  
                                    replaceTokens: true,
                                    targetLocation: "${env.BUILD_PROPERTIES_FILENAME}",
                                    variable: 'BUILD_PROPERTIES'
                                )
                            ]) {
                            withMaven(
                                globalMavenSettingsConfig: "${env.MAVEN_PROPERTIES_ID}"
                            ) {
								
								withCredentials([file(credentialsId: 'bithatch-gpg-signing', variable: 'GPGKEY')]) {
								    sh '''
								        gpg --import $GPGKEY
		                                mvn "-Dbuild.projectProperties=$BUILD_PROPERTIES" \
		                                    -U clean deploy -P sign
		                                '''
								}
								
                                
                            }
                        }
                    }
                }
                
				/*
				 * Linux AMD64 Installers and Packages
				 */
				stage ('Linux 64 bit AMD64 TNFS') {
					agent {
						label 'linux && x86_64'
					}
					steps {
                    
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULLVERSION}"
                        }
                        
						configFileProvider([
					 			configFile(
                                    fileId: "${env.BUILD_PROPERTIES_ID}",  
					 				replaceTokens: true,
                                    targetLocation: "${env.BUILD_PROPERTIES_FILENAME}",
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
                                globalMavenSettingsConfig: "${env.MAVEN_PROPERTIES_ID}"
					 		) {					 		  	
                                sh '''
                                mvn "-Dbuild.projectProperties=$BUILD_PROPERTIES" \
                                    -U clean package
                                '''
					 		}
        				}
					}
				}
                
				/*
				 * Linux AARCH64 Installers and Packages
				 */
				stage ('Linux 64 bit AARCH64 TNFS') {
					agent {
						label 'linux && aarch64'
					}
					steps {
                    
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULLVERSION}"
                        }
                        
						configFileProvider([
					 			configFile(
                                    fileId: "${env.BUILD_PROPERTIES_ID}",  
					 				replaceTokens: true,
                                    targetLocation: "${env.BUILD_PROPERTIES_FILENAME}",
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
                                globalMavenSettingsConfig: "${env.MAVEN_PROPERTIES_ID}"
					 		) {					 		  	
                                sh '''
                                mvn "-Dbuild.projectProperties=$BUILD_PROPERTIES" \
                                    -U clean package
                                '''
					 		}
        				}
					}
				}
                
				/*
				 * MacOS installers
				 */
				stage ('MacOS Intel TNFS') {
					agent {
						label 'macos && x86_64'
					}
					steps {
                    
                        script {
                            env.FULL_VERSION = getFullVersion()
                            echo "Full Version : ${env.FULLVERSION}"
                        }
                        
						configFileProvider([
					 			configFile(
                                    fileId: "${env.BUILD_PROPERTIES_ID}",  
					 				replaceTokens: true,
                                    targetLocation: "${env.BUILD_PROPERTIES_FILENAME}",
					 				variable: 'BUILD_PROPERTIES'
					 			)
					 		]) {
					 		withMaven(
                                globalMavenSettingsConfig: "${env.MAVEN_PROPERTIES_ID}"
					 		) {
                                sh 'mvn -U -P native-image clean package'
					 		}
        				}
                        
					}
				}
                
			}
		}
	}
}

String getFullVersion() {
    def pom = readMavenPom file: "pom.xml"
    pom_version_array = pom.version.split('\\.')
    suffix_array = pom_version_array[2].split('-')
    return pom_version_array[0] + '.' + pom_version_array[1] + "." + suffix_array[0] + "-${BUILD_NUMBER}"
}