pipeline {
 	agent none
 	tools {
		maven 'Maven 3.9.11' 
		jdk 'Graal 25' 
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
                        label 'linux && posix'
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
									
									withCredentials([usernamePassword(credentialsId: 'bithatch-ftp-upload', passwordVariable: 'FTP_UPLOAD_PASSWORD', usernameVariable: 'FTP_UPLOAD_USERNAME')]) {
										sh '''
									        gpg --import $GPGKEY
			                                mvn "-Dbuild.projectProperties=$BUILD_PROPERTIES" \
			                                    -U clean deploy -P sign,upload-distribution \
			                                    -Dbuild.uploadPassword="$FTP_UPLOAD_PASSWORD" -Dbuild.uploadUsername=$FTP_UPLOAD_USERNAME
			                                '''
										}
									
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
								withCredentials([usernamePassword(credentialsId: 'bithatch-ftp-upload', passwordVariable: 'FTP_UPLOAD_PASSWORD', usernameVariable: 'FTP_UPLOAD_USERNAME')]) {			 		  	
	                                sh '''
	                                mvn "-Dbuild.projectProperties=$BUILD_PROPERTIES" \
	                                    -U -P native-image,upload-distribution clean package \
			                            -Dbuild.uploadPassword="$FTP_UPLOAD_PASSWORD" -Dbuild.uploadUsername=$FTP_UPLOAD_USERNAME
	                                '''
	                            }
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
								withCredentials([usernamePassword(credentialsId: 'bithatch-ftp-upload', passwordVariable: 'FTP_UPLOAD_PASSWORD', usernameVariable: 'FTP_UPLOAD_USERNAME')]) {					 		  	
	                                sh '''
	                                mvn "-Dbuild.projectProperties=$BUILD_PROPERTIES" \
	                                    -U -P native-image,upload-distribution clean package \
				                        -Dbuild.uploadPassword="$FTP_UPLOAD_PASSWORD" -Dbuild.uploadUsername=$FTP_UPLOAD_USERNAME
	                                '''
	                            }
					 		}
        				}
					}
				}
                
				/*
				 * MacOS
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
								withCredentials([usernamePassword(credentialsId: 'bithatch-ftp-upload', passwordVariable: 'FTP_UPLOAD_PASSWORD', usernameVariable: 'FTP_UPLOAD_USERNAME')]) {			 		  	
	                                sh '''
	                                mvn "-Dbuild.projectProperties=$BUILD_PROPERTIES" \
	                                    -U -P native-image,upload-distribution clean package \
				                        -Dbuild.uploadPassword="$FTP_UPLOAD_PASSWORD" -Dbuild.uploadUsername=$FTP_UPLOAD_USERNAME
	                                '''
	                            }
					 		}
        				}
                        
					}
				}
				
				/*
				 * Windows
				 */
				stage ('Windows Intel TNFS') {
					agent {
						label 'windows && x86_64'
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
								withCredentials([usernamePassword(credentialsId: 'bithatch-ftp-upload', passwordVariable: 'FTP_UPLOAD_PASSWORD', usernameVariable: 'FTP_UPLOAD_USERNAME')]) {			 		  	
	                                bat '''
	                                mvn "-Dbuild.projectProperties=%BUILD_PROPERTIES%" \
	                                    -U -P native-image,upload-distribution clean package \
				                        -Dbuild.uploadPassword="%FTP_UPLOAD_PASSWORD%" -Dbuild.uploadUsername=%FTP_UPLOAD_USERNAME%
	                                '''
	                            }
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