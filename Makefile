# ==============================================================================
# Android SMS Forwarder - Build & Run Makefile
# ==============================================================================

# Auto-detect JAVA_HOME if not already set in environment
ifndef JAVA_HOME
  AS_JBR := $(shell if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then echo "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; fi)
  SYS_JAVA := $(shell /usr/libexec/java_home 2>/dev/null)
  ifneq ($(AS_JBR),)
    export JAVA_HOME := $(AS_JBR)
  else ifneq ($(SYS_JAVA),)
    export JAVA_HOME := $(SYS_JAVA)
  endif
endif

# Auto-detect ANDROID_HOME if not already set in environment
ifndef ANDROID_HOME
  DETECTED_SDK := $(shell if [ -d "$$HOME/Library/Android/sdk" ]; then echo "$$HOME/Library/Android/sdk"; fi)
  ifneq ($(DETECTED_SDK),)
    export ANDROID_HOME := $(DETECTED_SDK)
    export ANDROID_SDK_ROOT := $(DETECTED_SDK)
    export PATH := $(DETECTED_SDK)/platform-tools:$(DETECTED_SDK)/emulator:$(PATH)
  endif
endif

GRADLEW := ./gradlew
APP_PACKAGE := com.smsforwarder
MAIN_ACTIVITY := com.smsforwarder.MainActivity
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

.PHONY: all help check-env build assemble release bundle install run test lint clean logs apk-path

# Default target
all: help

## help: Display available make targets
help:
	@echo "======================================================================"
	@echo " Android SMS Forwarder - Build Commands"
	@echo "======================================================================"
	@echo "  make check-env   - Verify Java and Android SDK environment"
	@echo "  make build       - Build debug APK (alias for assemble)"
	@echo "  make assemble    - Compile and generate debug APK"
	@echo "  make release     - Compile and generate unsigned release APK"
	@echo "  make bundle      - Generate release Android App Bundle (.aab)"
	@echo "  make install     - Install debug APK onto connected device / emulator"
	@echo "  make run         - Launch main activity on connected device"
	@echo "  make test        - Run JVM unit tests"
	@echo "  make lint        - Run Android lint analysis"
	@echo "  make clean       - Clean all build outputs and Gradle cache"
	@echo "  make logs        - Stream live forwarder logcat from connected device"
	@echo "  make apk-path    - Print path to debug APK"
	@echo "======================================================================"

## check-env: Check environment prerequisites
check-env:
	@echo "--> Checking environment..."
	@if [ -n "$$JAVA_HOME" ] && [ -d "$$JAVA_HOME" ]; then \
		echo " [✓] JAVA_HOME is set: $$JAVA_HOME"; \
		"$$JAVA_HOME/bin/java" -version 2>&1 | head -n 1; \
	else \
		echo " [!] JAVA_HOME is not set or directory does not exist."; \
	fi
	@if [ -n "$$ANDROID_HOME" ] && [ -d "$$ANDROID_HOME" ]; then \
		echo " [✓] ANDROID_HOME is set: $$ANDROID_HOME"; \
	else \
		echo " [!] ANDROID_HOME is not set (install via Android Studio SDK Manager or set ANDROID_HOME)."; \
	fi
	@if command -v adb >/dev/null 2>&1; then \
		echo " [✓] adb found: $$(which adb)"; \
		adb devices; \
	elif [ -n "$$ANDROID_HOME" ] && [ -f "$$ANDROID_HOME/platform-tools/adb" ]; then \
		echo " [✓] adb found at $$ANDROID_HOME/platform-tools/adb"; \
	else \
		echo " [i] adb not found in PATH or ANDROID_HOME/platform-tools"; \
	fi

## assemble: Build debug APK
assemble:
	@echo "--> Building Debug APK..."
	@chmod +x $(GRADLEW) 2>/dev/null || true
	$(GRADLEW) assembleDebug
	@echo " [✓] Build finished: $(DEBUG_APK)"

build: assemble

## release: Build release APK
release:
	@echo "--> Building Release APK..."
	@chmod +x $(GRADLEW) 2>/dev/null || true
	$(GRADLEW) assembleRelease
	@echo " [✓] Release APK generated in app/build/outputs/apk/release/"

## bundle: Build release App Bundle (AAB)
bundle:
	@echo "--> Building App Bundle..."
	@chmod +x $(GRADLEW) 2>/dev/null || true
	$(GRADLEW) bundleRelease

## install: Install debug APK to connected device
install: assemble
	@echo "--> Installing Debug APK on device..."
	$(GRADLEW) installDebug

## run: Launch app on device
run:
	@echo "--> Launching $(APP_PACKAGE)..."
	adb shell am start -n $(APP_PACKAGE)/$(MAIN_ACTIVITY)

## test: Run unit tests
test:
	@echo "--> Running unit tests..."
	@chmod +x $(GRADLEW) 2>/dev/null || true
	$(GRADLEW) test

## lint: Run Android linter
lint:
	@echo "--> Running lint checks..."
	@chmod +x $(GRADLEW) 2>/dev/null || true
	$(GRADLEW) lint

## clean: Clean Gradle build cache and generated files
clean:
	@echo "--> Cleaning build artifacts..."
	@chmod +x $(GRADLEW) 2>/dev/null || true
	$(GRADLEW) clean

## logs: Stream real-time logcat for forwarder components
logs:
	@echo "--> Streaming logcat for SMS Forwarder..."
	adb logcat -v time -s "SmsReceiver" "ForwardWorker" "TelegramSender" "WhatsAppSender" "SmsForwarder" "SimUtil" "SecurePrefs"

## apk-path: Show debug APK location
apk-path:
	@echo "$(DEBUG_APK)"
