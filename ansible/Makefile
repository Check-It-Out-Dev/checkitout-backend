# Makefile for Instagram Platform Ansible
# Convenient shortcuts for common tasks

.PHONY: help install test lint syntax dry-run deploy start verify clean

# Default target
help:
	@echo "Instagram Platform Ansible - Available Commands"
	@echo ""
	@echo "Setup:"
	@echo "  make install          Install Ansible and dependencies"
	@echo "  make setup-vault      Create vault password file"
	@echo ""
	@echo "Testing:"
	@echo "  make test             Run all tests (syntax, lint, molecule)"
	@echo "  make lint             Run ansible-lint and yamllint"
	@echo "  make syntax           Check Ansible syntax"
	@echo "  make dry-run          Dry run deployment (--check)"
	@echo "  make molecule         Run Molecule tests"
	@echo ""
	@echo "Deployment:"
	@echo "  make deploy           Deploy complete infrastructure"
	@echo "  make start            Start applications"
	@echo "  make verify           Run verification tests"
	@echo "  make full             Deploy + Start + Verify (complete workflow)"
	@echo ""
	@echo "Utilities:"
	@echo "  make clean            Clean temporary files"
	@echo "  make docs             View documentation"

# Installation
install:
	pip install ansible ansible-lint yamllint molecule "molecule[docker]"
	ansible-galaxy install -r requirements.yml

setup-vault:
	@echo "Creating vault password file..."
	@read -sp "Enter vault password: " password; echo $$password > ~/.ansible/vault-pass
	@chmod 600 ~/.ansible/vault-pass
	@echo "\nVault password file created at ~/.ansible/vault-pass"

# Testing
test: syntax lint molecule

lint:
	@echo "Running yamllint..."
	yamllint .
	@echo "Running ansible-lint..."
	ansible-lint playbooks/site.yml

syntax:
	@echo "Checking Ansible syntax..."
	ansible-playbook -i inventory/production.yml playbooks/site.yml --syntax-check
	ansible-playbook -i inventory/production.yml playbooks/startup.yml --syntax-check
	ansible-playbook -i inventory/production.yml playbooks/verify.yml --syntax-check

dry-run:
	@echo "Running dry run (--check)..."
	ansible-playbook -i inventory/production.yml playbooks/site.yml \
		--check \
		--diff

molecule:
	@echo "Running Molecule tests..."
	molecule test

# Deployment
deploy:
	@echo "Deploying infrastructure..."
	ansible-playbook -i inventory/production.yml playbooks/site.yml

start:
	@echo "Starting applications..."
	ansible-playbook -i inventory/production.yml playbooks/startup.yml

verify:
	@echo "Running verification..."
	ansible-playbook -i inventory/production.yml playbooks/verify.yml

full: deploy start verify
	@echo "Complete deployment finished!"

# Deploy specific components
infra-only:
	ansible-playbook -i inventory/production.yml playbooks/infra-only.yml

apps-only:
	ansible-playbook -i inventory/production.yml playbooks/apps-only.yml

# Utilities
clean:
	@echo "Cleaning temporary files..."
	rm -rf .molecule/
	rm -rf .cache/
	rm -f *.retry
	rm -f ansible.log
	find . -type f -name "*.pyc" -delete
	find . -type d -name "__pycache__" -delete

docs:
	@echo "Available documentation:"
	@echo "  - README.md (main documentation)"
	@echo "  - TESTING.md (testing guide)"
	@echo "  - CONTRIBUTING.md (contribution guidelines)"
	@echo "  - TROUBLESHOOTING.md (common issues)"
	@echo "  - DEPLOYMENT-GUIDE.md (deployment walkthrough)"
	@echo ""
	@echo "Role documentation:"
	@ls -1 roles/*/README.md | sed 's/roles\//  - /'

# Quick checks
check: syntax lint
	@echo "All checks passed!"
